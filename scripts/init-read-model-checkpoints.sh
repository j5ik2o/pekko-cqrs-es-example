#!/bin/bash

# JournalHead の sequence-nr で read_model_checkpoints を初期化する。
# 既存環境の JournalHead マイグレーション後に使う。

set -euo pipefail

cd "$(dirname "$0")/.."

usage() {
  cat <<'EOF'
JournalHead の sequence-nr で read_model_checkpoints を初期化する

既存環境の JournalHead マイグレーション後に使います。
Read Model が JournalHead の sequence-nr まで追いついている場合だけ実行してください。
追いついていない場合は、このスクリプトを使わず Journal から Read Model を再構築してください。

使い方:
  ./scripts/init-read-model-checkpoints.sh              # dry-run
  ./scripts/init-read-model-checkpoints.sh --execute    # checkpoint を更新

接続先:
  DYNAMODB_ENDPOINT (既定: J5IK2O_DYNAMO_DB_JOURNAL_DYNAMO_DB_CLIENT_ENDPOINT
                     または http://localhost:${DOCKER_LOCALSTACK_PORT:-50503})
  PSQL_URL を設定すると、その接続文字列で psql を実行します。
  例: PSQL_URL='postgresql://user:pass@host:5432/db'
  PSQL_URL が未設定の場合は docker compose の postgres サービスに接続します。
  PSQL_BIN で psql の実行ファイルを変更できます (既定: psql)。
EOF
}

MODE="dry-run"
while [ "$#" -gt 0 ]; do
  case "$1" in
    --execute)
      MODE="execute"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

export AWS_REGION="${AWS_REGION:-ap-northeast-1}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-$AWS_REGION}"

LOCALSTACK_PORT="${DOCKER_LOCALSTACK_PORT:-50503}"
DYNAMODB_ENDPOINT="${DYNAMODB_ENDPOINT:-${J5IK2O_DYNAMO_DB_JOURNAL_DYNAMO_DB_CLIENT_ENDPOINT:-http://localhost:${LOCALSTACK_PORT}}}"
HEAD_TABLE="${J5IK2O_DYNAMO_DB_JOURNAL_HEAD_TABLE_NAME:-JournalHead}"

POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_DB="${POSTGRES_DB:-p-cqrs-es_development}"
PSQL_BIN="${PSQL_BIN:-psql}"

ENDPOINT_ARGS=(--endpoint-url "$DYNAMODB_ENDPOINT")

is_local_endpoint() {
  case "$1" in
    http://localhost:*|http://127.0.0.1:*|http://localstack:*) return 0 ;;
    *) return 1 ;;
  esac
}

# ローカルの LocalStack 以外では、IAM ロールや AWS_PROFILE を上書きしない。
if is_local_endpoint "$DYNAMODB_ENDPOINT"; then
  export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-dummy}"
  export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-dummy}"
fi

psql_exec() {
  if [ -n "${PSQL_URL:-}" ]; then
    "$PSQL_BIN" -v ON_ERROR_STOP=1 "$PSQL_URL"
  else
    docker compose -f docker-compose-common.yml -f docker-compose-local.yml \
      exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1
  fi
}

if ! printf 'select 1 from read_model_checkpoints limit 1;\n' | psql_exec >/dev/null 2>&1; then
  echo "read_model_checkpoints テーブルに接続できません。" >&2
  echo "先に Flyway マイグレーションを実行し、PostgreSQL に接続できる状態にしてください。" >&2
  exit 1
fi

echo "Scanning ${HEAD_TABLE} at ${DYNAMODB_ENDPOINT}..."
all_items='[]'
next_token=''

while :; do
  if [ -z "$next_token" ]; then
    response=$(aws dynamodb scan \
      "${ENDPOINT_ARGS[@]}" \
      --table-name "$HEAD_TABLE" \
      --projection-expression "#pid, #seq" \
      --expression-attribute-names '{"#pid":"persistence-id","#seq":"sequence-nr"}' \
      --max-items 100 \
      --output json)
  else
    response=$(aws dynamodb scan \
      "${ENDPOINT_ARGS[@]}" \
      --table-name "$HEAD_TABLE" \
      --projection-expression "#pid, #seq" \
      --expression-attribute-names '{"#pid":"persistence-id","#seq":"sequence-nr"}' \
      --max-items 100 \
      --starting-token "$next_token" \
      --output json)
  fi

  # JSON を jq の引数にすると、件数が多いときに E2BIG になるため標準入力で渡す。
  all_items=$(printf '%s\n%s\n' "$all_items" "$response" | jq -sc '.[0] + (.[1].Items // [])')
  next_token=$(jq -r '.NextToken // empty' <<<"$response")
  if [ -z "$next_token" ]; then
    break
  fi
done

checkpoints=$(jq -c \
  '[.[] | {persistence_id: .["persistence-id"].S, sequence_nr: (.["sequence-nr"].N | tonumber)}]' \
  <<<"$all_items")
count=$(jq 'length' <<<"$checkpoints")

if [ "$count" -eq 0 ]; then
  echo "JournalHead にアイテムがありません。先に migrate-journal-head.sh --execute を実行してください。"
  exit 0
fi

echo "Found ${count} JournalHead items."
jq -r '.[:10][] | "  - \(.persistence_id): \(.sequence_nr)"' <<<"$checkpoints"
if [ "$count" -gt 10 ]; then
  echo "  ..."
fi

if [ "$MODE" = "dry-run" ]; then
  echo ""
  echo "Dry-run: checkpoint は更新していません。--execute で更新します。"
  exit 0
fi

SQL=$(cat <<SQL
with migrated_checkpoints as (
  select (item->>'persistence_id') as persistence_id,
         (item->>'sequence_nr')::bigint as sequence_nr
  from jsonb_array_elements(\$json\$${checkpoints}\$json\$::jsonb) as item
)
insert into read_model_checkpoints (persistence_id, sequence_nr, updated_at)
select persistence_id, sequence_nr, current_timestamp
from migrated_checkpoints
on conflict (persistence_id) do update
set sequence_nr = greatest(read_model_checkpoints.sequence_nr, excluded.sequence_nr),
    updated_at = case
      when excluded.sequence_nr > read_model_checkpoints.sequence_nr then current_timestamp
      else read_model_checkpoints.updated_at
    end;
SQL
)

echo ""
echo "Updating read_model_checkpoints (only forward, never lower)..."
printf '%s\n' "$SQL" | psql_exec
echo "Updated ${count} checkpoints."
