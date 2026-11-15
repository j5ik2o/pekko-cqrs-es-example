#!/usr/bin/env python3
"""Run the E2E CLI with fake HTTP responses, without services or Docker."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import tempfile

CASES = {'normal': 0, 'same-name': 0, 'other-id-only': 1, 'wrong-name': 1,
         'wrong-id-query': 1, 'id-query-error': 1}
FAILURE_DIAGNOSTICS = {
    'other-id-only': 'UserAccount not found in database',
    'wrong-name': 'User data mismatch!',
    'wrong-id-query': 'Failed to query UserAccount by ID',
    'id-query-error': 'Failed to query UserAccount by ID',
}

mock = '''#!/usr/bin/env python3
import json, os, sys
if '-w' in sys.argv:
    print('{}\\n200')
    raise SystemExit
payload = json.loads(sys.argv[sys.argv.index('-d') + 1])
query = payload['query']
mode = os.environ['RESPONSE_CASE']
first = '太郎1700000000'
row = {'id': 'created-id', 'firstName': first, 'lastName': 'テスト'}
if 'createUserAccount' in query:
    result = {'data': {'createUserAccount': {'id': 'created-id'}}}
elif 'getUserAccount(' in query:
    if mode == 'id-query-error':
        result = {'data': {'getUserAccount': None}, 'errors': [{'message': 'query failed'}]}
    else:
        if mode == 'wrong-id-query': row['id'] = 'another-id'
        result = {'data': {'getUserAccount': row}}
else:
    rows = [row]
    if mode == 'same-name': rows = [dict(row, id='another-id'), row]
    if mode == 'other-id-only': rows = [dict(row, id='another-id')]
    if mode == 'wrong-name': rows = [dict(row, firstName='wrong')]
    result = {'data': {'getUserAccounts': rows}}
print(json.dumps(result, ensure_ascii=False))
'''

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('checkout', type=Path, help='Target Git checkout')
    parser.add_argument('--script', type=Path, default=Path('scripts/test-e2e.sh'),
                        help='CLI path, relative to checkout or absolute')
    parser.add_argument('--output-dir', type=Path, default=Path('target/e2e-response-fixtures'),
                        help='Logs and report, relative to checkout or absolute')
    parser.add_argument('--case', choices=CASES,
                        help='Run one fixture, with a normal control for a negative case')
    args = parser.parse_args()
    checkout = args.checkout.resolve()
    script = (checkout / args.script).resolve()
    logs = (checkout / args.output_dir).resolve()
    if not script.is_file():
        parser.error(f'Script not found: {script}')
    for tool in ('bash', 'jq', 'git'):
        if not shutil.which(tool):
            parser.error(f'Required tool not found: {tool}')
    logs.mkdir(parents=True, exist_ok=True)
    revision = subprocess.check_output(
        ['git', '-C', str(checkout), 'rev-parse', 'HEAD'], text=True).strip()
    status = subprocess.check_output(
        ['git', '-C', str(checkout), 'status', '--short'], text=True)
    command = shlex.join([sys.executable, str(Path(__file__).resolve()), str(checkout),
                          '--script', str(script), '--output-dir', str(logs)] +
                         (['--case', args.case] if args.case else []))
    report = {'revision': revision, 'working_tree_status': status,
              'script': str(script), 'script_sha256': hashlib.sha256(script.read_bytes()).hexdigest(),
              'reproduce': command, 'cases': []}
    print(f'Target revision: {revision}')
    print(f'Reproduce: {command}')
    failed = []
    selected_cases = list(CASES)
    if args.case:
        selected_cases = [args.case]
        if CASES[args.case] != 0:
            selected_cases.insert(0, 'normal')
    with tempfile.TemporaryDirectory(prefix='e2e-response-fixtures-') as temp:
        bin_dir = Path(temp)
        for name, contents in [('curl', mock), ('date', '#!/bin/sh\nprintf 1700000000\n')]:
            file = bin_dir / name
            file.write_text(contents, encoding='utf-8')
            file.chmod(0o755)
        for case in selected_cases:
            expected = CASES[case]
            env = dict(os.environ, PATH=str(bin_dir) + os.pathsep + os.environ['PATH'],
                       RESPONSE_CASE=case, E2E_MAX_RETRIES='1',
                       E2E_WAIT_AFTER_CREATE='0', E2E_RETRY_DELAY='0')
            with (logs / f'{case}.stdout.log').open('w') as stdout, (
                    logs / f'{case}.stderr.log').open('w') as stderr:
                try:
                    run = subprocess.run(['bash', str(script)], cwd=checkout, env=env,
                                         stdout=stdout, stderr=stderr, timeout=20)
                    observed = run.returncode
                except subprocess.TimeoutExpired:
                    observed = 'timeout'
            diagnostic = FAILURE_DIAGNOSTICS.get(case)
            diagnostic_matched = diagnostic is None or diagnostic in (logs / f'{case}.stdout.log').read_text()
            passed = observed == expected and diagnostic_matched
            report['cases'].append({'case': case, 'expected': expected, 'observed': observed,
                                    'expected_diagnostic': diagnostic,
                                    'diagnostic_matched': diagnostic_matched, 'passed': passed})
            print(f'{case}: expected {expected}, observed {observed} {"PASS" if passed else "FAIL"}')
            if not passed:
                failed.append(case)
                if not diagnostic_matched:
                    print(f'Missing expected failure diagnostic: {diagnostic}')
                for stream in ('stdout', 'stderr'):
                    path = logs / f'{case}.{stream}.log'
                    print(f'--- {path} ---')
                    print(path.read_text())
    (logs / 'report.json').write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(f'Report and CLI logs: {logs}')
    if failed:
        print('FAIL: ' + ', '.join(failed))
        return 1
    print('PASS: CLI response fixtures (fake transport, not a stream E2E run)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
