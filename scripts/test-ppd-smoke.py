#!/usr/bin/env python3
"""Exercise the published PPD through command-api and offline migration."""
import json
import os
from pathlib import Path
import socket
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
LOGS = ROOT / 'target/ppd-smoke'
ENV = os.environ.copy()
ENV.update(AWS_ACCESS_KEY_ID='dummy', AWS_SECRET_ACCESS_KEY='dummy',
           AWS_REGION='ap-northeast-1', AWS_DEFAULT_REGION='ap-northeast-1', AWS_PAGER='')
MIGRATION = 'com.github.j5ik2o.pekko.persistence.dynamodb.journal.head.JournalHeadMigration'


def run(args, **kwargs):
    return subprocess.run(args, cwd=ROOT, env=ENV, check=True, timeout=600, **kwargs)


def aws(*args):
    return json.loads(run(['aws', 'dynamodb', '--endpoint-url', ENDPOINT,
                           '--output', 'json', '--cli-connect-timeout', '3', '--cli-read-timeout', '5', *args], capture_output=True, text=True).stdout or '{}')


def wait_until(check, description, attempts=60):
    for _ in range(attempts):
        try:
            if check():
                return
        except (OSError, subprocess.CalledProcessError):
            pass
        time.sleep(1)
    raise RuntimeError('Timed out waiting for ' + description)


def heads():
    return aws('scan', '--table-name', 'JournalHead', '--consistent-read')['Items']


def migrate(mode):
    with (LOGS / ('migration-' + mode + '.log')).open('w') as log:
        run(['sbt', '-batch', 'commandApi/runMain ' + MIGRATION +
             (' --execute' if mode != 'dry-run' else '')], stdout=log, stderr=subprocess.STDOUT)


def create_user(port):
    payload = {'query': 'mutation($input: CreateUserAccountInput!) { createUserAccount(input: $input) { id } }',
               'variables': {'input': {'firstName': 'Smoke', 'lastName': 'PPD',
                                       'emailAddress': 'ppd-smoke@example.com'}}}
    request = urllib.request.Request(f'http://127.0.0.1:{port}/api/graphql',
                                     json.dumps(payload).encode(),
                                     {'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=30) as response:
        result = json.load(response)
    assert not result.get('errors'), result
    return result['data']['createUserAccount']['id']


def stop(process):
    process.terminate()
    try:
        process.wait(timeout=20)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=10)


def main():
    global ENDPOINT
    LOGS.mkdir(parents=True, exist_ok=True)
    version = run(['java', '-version'], capture_output=True, text=True).stderr
    assert 'version "21.' in version, 'Use Java 21, matching CI. Found ' + version
    run(['sbt', '-batch', 'commandApi/Universal/stage'])
    container = run(['docker', 'run', '-d', '-p', '127.0.0.1::8000',
                     'amazon/dynamodb-local:2.6.1', '-jar', 'DynamoDBLocal.jar',
                     '-inMemory', '-sharedDb'], capture_output=True, text=True).stdout.strip()
    process = None
    try:
        binding = run(['docker', 'port', container, '8000/tcp'], capture_output=True, text=True).stdout.strip()
        ENDPOINT = 'http://' + binding
        ENV.update(J5IK2O_DYNAMO_DB_JOURNAL_DYNAMO_DB_CLIENT_ENDPOINT=ENDPOINT,
                   J5IK2O_DYNAMO_DB_SNAPSHOT_DYNAMO_DB_CLIENT_ENDPOINT=ENDPOINT,
                   J5IK2O_DYNAMO_DB_JOURNAL_TABLE_NAME='Journal',
                   J5IK2O_DYNAMO_DB_JOURNAL_HEAD_TABLE_NAME='JournalHead',
                   J5IK2O_DYNAMO_DB_SNAPSHOT_TABLE_NAME='Snapshot',
                   PEKKO_CLUSTER_ENABLED='false')
        wait_until(lambda: aws('list-tables'), 'DynamoDB Local')
        for table in ('journal-table', 'journal-head-table', 'snapshot-table'):
            aws('create-table', '--cli-input-json', 'file://' + str(ROOT / 'tools/dynamodb-setup' / (table + '.json')))
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0))
            port = listener.getsockname()[1]
        ENV['COMMAND_API_SERVER_PORT'] = str(port)
        with (LOGS / 'command-api.log').open('w') as log:
            process = subprocess.Popen([str(ROOT / 'apps/command-api/target/universal/stage/bin/command-api')],
                                       cwd=ROOT, env=ENV, stdout=log, stderr=subprocess.STDOUT)
            def ready():
                if process.poll() is not None:
                    raise RuntimeError('command-api exited. See target/ppd-smoke/command-api.log')
                with urllib.request.urlopen(f'http://127.0.0.1:{port}/api/health', timeout=2) as response:
                    return response.status == 200
            wait_until(ready, 'command-api')
            user_id = create_user(port)
            stop(process)
            process = None
        rows = aws('scan', '--table-name', 'Journal', '--consistent-read')['Items']
        original = heads()
        (LOGS / 'persisted.json').write_text(json.dumps({'journal': rows, 'head': original}, indent=2))
        assert len(rows) == 1 and len(original) == 1, (rows, original)
        row, head = rows[0], original[0]
        pid = row['persistence-id']['S']
        assert user_id in pid and head['persistence-id']['S'] == pid
        assert row['sequence-nr']['N'] == head['sequence-nr']['N'] == '1'
        assert row['message']['B'] and row['pkey']['S'] and row['skey']['S']
        events = head['events']['L']
        assert len(events) == 1 and events[0]['M']['message']['B'] == row['message']['B'], events
        assert events[0]['M']['sequence-nr']['N'] == '1'
        aws('delete-item', '--table-name', 'JournalHead', '--key', json.dumps({'persistence-id': {'S': pid}}))
        migrate('dry-run')
        assert heads() == [], 'dry-run wrote a Head'
        migrate('execute')
        migrated = heads()
        assert migrated == [{'persistence-id': {'S': pid}, 'sequence-nr': {'N': '1'}, 'events': {'L': []}}], migrated
        migrate('execute-again')
        assert heads() == migrated, 'migration was not idempotent'
        assert aws('scan', '--table-name', 'Journal', '--consistent-read')['Items'] == rows
        print('PASS: command-api persisted Journal/Head; migration dry-run, execute and repeat verified.')
        print('RMU stream consumption requires the separate LocalStack/PostgreSQL E2E.')
    finally:
        if process is not None:
            stop(process)
        try:
            with (LOGS / 'dynamodb-local.log').open('w') as log:
                run(['docker', 'logs', container], stdout=log, stderr=subprocess.STDOUT)
        finally:
            run(['docker', 'rm', '-f', container], capture_output=True)


if __name__ == '__main__':
    main()
