#!/usr/bin/env python3
"""Verify that RMU notifies an external subscriber after the read model is updated.

Requires the LocalStack/PostgreSQL stack and the Lambda deployed by
scripts/deploy-lambda-localstack.sh. Subscribes an SQS FIFO queue to the
notification topic, creates a user through the Command API, waits for the
notification, and then reads the user from the Query API right away.
"""
import json
import os
import subprocess
import time
import urllib.request

ENDPOINT = os.environ.get('LOCALSTACK_ENDPOINT', 'http://localhost:50503')
COMMAND_API = os.environ.get('COMMAND_API_ENDPOINT', 'http://localhost:50501/api/graphql')
QUERY_API = os.environ.get('QUERY_API_ENDPOINT', 'http://localhost:50502/api/graphql')
TOPIC_NAME = os.environ.get('NOTIFICATION_TOPIC_NAME', 'pcqrses-read-model-updated.fifo')
QUEUE_NAME = os.environ.get('NOTIFICATION_QUEUE_NAME', 'pcqrses-read-model-updated-e2e.fifo')
WAIT_SECONDS = int(os.environ.get('NOTIFICATION_WAIT_SECONDS', '120'))
ENV = dict(os.environ, AWS_ACCESS_KEY_ID='dummy', AWS_SECRET_ACCESS_KEY='dummy',
           AWS_DEFAULT_REGION='ap-northeast-1', AWS_PAGER='')


def aws(*args):
    result = subprocess.run(['aws', '--endpoint-url', ENDPOINT, '--output', 'json', *args],
                            env=ENV, check=True, capture_output=True, text=True, timeout=60)
    return json.loads(result.stdout or '{}')


def graphql(endpoint, query, variables):
    request = urllib.request.Request(endpoint, json.dumps({'query': query, 'variables': variables}).encode(),
                                     {'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=30) as response:
        result = json.load(response)
    assert not result.get('errors'), result
    return result['data']


def subscribe_queue():
    topic_arn = aws('sns', 'create-topic', '--name', TOPIC_NAME,
                    '--attributes', 'FifoTopic=true')['TopicArn']
    queue_url = aws('sqs', 'create-queue', '--queue-name', QUEUE_NAME,
                    '--attributes', 'FifoQueue=true')['QueueUrl']
    queue_arn = aws('sqs', 'get-queue-attributes', '--queue-url', queue_url,
                    '--attribute-names', 'QueueArn')['Attributes']['QueueArn']
    aws('sns', 'subscribe', '--topic-arn', topic_arn, '--protocol', 'sqs',
        '--notification-endpoint', queue_arn, '--attributes', 'RawMessageDelivery=true')
    aws('sqs', 'purge-queue', '--queue-url', queue_url)
    return topic_arn, queue_url


def create_user():
    stamp = str(int(time.time()))
    data = graphql(COMMAND_API,
                   'mutation($input: CreateUserAccountInput!) { createUserAccount(input: $input) { id } }',
                   {'input': {'firstName': 'Notify' + stamp, 'lastName': 'RMU',
                              'emailAddress': f'notify{stamp}@example.com'}})
    return data['createUserAccount']['id'], 'Notify' + stamp


def wait_for_notification(queue_url, persistence_id):
    deadline = time.time() + WAIT_SECONDS
    seen = []
    while time.time() < deadline:
        messages = aws('sqs', 'receive-message', '--queue-url', queue_url,
                       '--max-number-of-messages', '10', '--wait-time-seconds', '5',
                       '--attribute-names', 'All').get('Messages', [])
        for message in messages:
            body = json.loads(message['Body'])
            seen.append(body)
            if body.get('persistenceId') == persistence_id:
                return body, message
            aws('sqs', 'delete-message', '--queue-url', queue_url,
                '--receipt-handle', message['ReceiptHandle'])
    raise RuntimeError(f'no notification for {persistence_id} within {WAIT_SECONDS}s; saw {seen}')


def main():
    topic_arn, queue_url = subscribe_queue()
    print(f'Subscribed {queue_url} to {topic_arn}')
    user_id, first_name = create_user()
    persistence_id = 'UserAccount-' + user_id
    print(f'Created {persistence_id}')
    body, message = wait_for_notification(queue_url, persistence_id)
    # 通知を受け取ったら、ほかの呼び出しを挟まずにリードAPIを叩く。RMU が更新を確定してから通知していれば、ここで見つかる
    found = graphql(QUERY_API,
                    'query($id: String!) { getUserAccount(userAccountId: $id) { id firstName } }',
                    {'id': user_id})['getUserAccount']
    aws('sqs', 'delete-message', '--queue-url', queue_url, '--receipt-handle', message['ReceiptHandle'])
    attributes = message['Attributes']
    print('Notification:', json.dumps(body), 'MessageGroupId:', attributes.get('MessageGroupId'))
    print('Query API right after the notification:', json.dumps(found, ensure_ascii=False))
    assert body == {'type': 'ReadModelUpdated', 'persistenceId': persistence_id, 'sequenceNr': 1}, body
    assert attributes.get('MessageGroupId') == persistence_id, attributes
    assert found == {'id': user_id, 'firstName': first_name}, found
    print('PASS: RMU notified after the read model was updated.')


if __name__ == '__main__':
    main()
