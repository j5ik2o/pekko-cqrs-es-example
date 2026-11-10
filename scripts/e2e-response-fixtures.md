# Run the E2E CLI response fixtures

Run from the repository root with Python 3, Bash, jq, and Git on PATH.

```sh
python3 scripts/test-e2e-response-fixtures.py .
```

The runner invokes the whole `scripts/test-e2e.sh` CLI. Temporary PATH entries
replace curl responses and date. Bash, jq, and the CLI's validation run normally.
No services or Docker are needed. These fixtures test CLI response handling.
They do not verify DynamoDB Streams, Lambda, or PostgreSQL delivery.
CI also retains the separate LocalStack and PostgreSQL stream E2E.

| Fixture | Expected CLI exit |
| --- | --- |
| normal | 0 |
| same-name | 0 |
| other-id-only | 1 |
| wrong-name | 1 |
| wrong-id-query | 1 |
| id-query-error | 1 |

The same-name fixture returns another account with the same name before the
created account. The CLI must find the created ID and verify the ID query.
Negative fixtures must also emit the diagnostic for the expected failure stage.
An unrelated exit 1, such as a missing tool, does not satisfy the fixture.

To target a checkout or script from another directory, pass their paths.
Relative script and output paths resolve against the target checkout.

```sh
python3 /path/to/runner/scripts/test-e2e-response-fixtures.py /path/to/checkout \
  --script scripts/test-e2e.sh --case same-name \
  --output-dir target/e2e-response-fixtures-same-name
```

Selecting a negative fixture also runs the normal fixture as a control, so
an environment that fails every invocation cannot pass a negative-only run.

The runner exits 1 when a case differs from its expected exit code or times out.
It prints failing stdout and stderr. Each run writes per-case logs and
`report.json` under `target/e2e-response-fixtures` by default. The report records
the target HEAD, working tree status, script SHA-256, reproduction command,
and expected and observed exits. Retain that directory with the tested revision
and any local patch when diagnosing an uncommitted change. Use separate output
directories to retain runs before and after a change. CI uploads these diagnostics
even when the fixture step fails.
