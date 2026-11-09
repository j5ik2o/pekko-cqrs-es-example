#!/bin/bash

# 既存環境の JournalHead マイグレーションとプリフライト
#
# 使い方:
#   ./scripts/migrate-journal-head.sh              # dry-run
#   ./scripts/migrate-journal-head.sh --execute    # JournalHead アイテムを作成/更新
#   ./scripts/migrate-journal-head.sh --preflight  # ストリーム設定の確認
#
# 前提:
#   - すべての書き込みノードを停止していること
#   - JournalHead テーブルと read_model_checkpoints テーブルを作成済みであること
#   - 既存の Read Model が Journal の最新シーケンス番号まで追いついていること
#
# 接続先は環境変数で上書きできます。
#   J5IK2O_DYNAMO_DB_JOURNAL_DYNAMO_DB_CLIENT_ENDPOINT
#   J5IK2O_DYNAMO_DB_SNAPSHOT_DYNAMO_DB_CLIENT_ENDPOINT
#   J5IK2O_DYNAMO_DB_JOURNAL_TABLE_NAME
#   J5IK2O_DYNAMO_DB_JOURNAL_HEAD_TABLE_NAME
#   J5IK2O_DYNAMO_DB_SNAPSHOT_TABLE_NAME

set -euo pipefail

cd "$(dirname "$0")/.."

usage() {
  sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
}

MODE="dry-run"
while [ "$#" -gt 0 ]; do
  case "$1" in
    --execute)
      MODE="execute"
      shift
      ;;
    --preflight)
      MODE="preflight"
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
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-dummy}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-dummy}"

LOCALSTACK_PORT="${DOCKER_LOCALSTACK_PORT:-50503}"
export J5IK2O_DYNAMO_DB_JOURNAL_DYNAMO_DB_CLIENT_ENDPOINT="${J5IK2O_DYNAMO_DB_JOURNAL_DYNAMO_DB_CLIENT_ENDPOINT:-http://localhost:${LOCALSTACK_PORT}}"
export J5IK2O_DYNAMO_DB_SNAPSHOT_DYNAMO_DB_CLIENT_ENDPOINT="${J5IK2O_DYNAMO_DB_SNAPSHOT_DYNAMO_DB_CLIENT_ENDPOINT:-http://localhost:${LOCALSTACK_PORT}}"

if [ -z "${PROTOC_PATH:-}" ] && command -v protoc >/dev/null 2>&1; then
  export PROTOC_PATH
  PROTOC_PATH="$(command -v protoc)"
fi

JOURNAL_TABLE="${J5IK2O_DYNAMO_DB_JOURNAL_TABLE_NAME:-Journal}"
HEAD_TABLE="${J5IK2O_DYNAMO_DB_JOURNAL_HEAD_TABLE_NAME:-JournalHead}"
SNAPSHOT_TABLE="${J5IK2O_DYNAMO_DB_SNAPSHOT_TABLE_NAME:-Snapshot}"

MIGRATION_CLASS="com.github.j5ik2o.pekko.persistence.dynamodb.journal.head.JournalHeadMigration"

ENDPOINT_ARGS=()
if [ -n "${J5IK2O_DYNAMO_DB_JOURNAL_DYNAMO_DB_CLIENT_ENDPOINT:-}" ]; then
  ENDPOINT_ARGS=(--endpoint-url "$J5IK2O_DYNAMO_DB_JOURNAL_DYNAMO_DB_CLIENT_ENDPOINT")
fi

echo "Journal : ${JOURNAL_TABLE}"
echo "JournalHead : ${HEAD_TABLE}"
echo "Snapshot : ${SNAPSHOT_TABLE}"
echo "Endpoint : ${J5IK2O_DYNAMO_DB_JOURNAL_DYNAMO_DB_CLIENT_ENDPOINT}"
echo ""

preflight() {
  local head_stream journal_stream
  local head_enabled head_view journal_enabled

  if ! head_stream=$(aws dynamodb describe-table "${ENDPOINT_ARGS[@]}" \
    --table-name "$HEAD_TABLE" \
    --query 'Table.StreamSpecification' \
    --output json 2>/dev/null); then
    echo "JournalHead preflight failed: ${HEAD_TABLE} テーブルが見つかりません。" >&2
    return 1
  fi
  if ! journal_stream=$(aws dynamodb describe-table "${ENDPOINT_ARGS[@]}" \
    --table-name "$JOURNAL_TABLE" \
    --query 'Table.StreamSpecification' \
    --output json 2>/dev/null); then
    echo "JournalHead preflight failed: ${JOURNAL_TABLE} テーブルが見つかりません。" >&2
    return 1
  fi

  head_enabled=$(jq -r '.StreamEnabled // false' <<<"$head_stream")
  head_view=$(jq -r '.StreamViewType // empty' <<<"$head_stream")
  journal_enabled=$(jq -r '.StreamEnabled // false' <<<"$journal_stream")

  if [ "$head_enabled" != "true" ]; then
    echo "JournalHead preflight failed: ${HEAD_TABLE} の DynamoDB Streams が無効です。NEW_IMAGE で有効化してください。" >&2
    return 1
  fi

  case "$head_view" in
    NEW_IMAGE|NEW_AND_OLD_IMAGES)
      ;;
    *)
      echo "JournalHead preflight failed: ${HEAD_TABLE} のストリーム種別が ${head_view:-<none>} です。NEW_IMAGE または NEW_AND_OLD_IMAGES を使ってください。" >&2
      return 1
      ;;
  esac

  if [ "$journal_enabled" = "true" ]; then
    echo "JournalHead preflight failed: ${JOURNAL_TABLE} の DynamoDB Streams が有効です。ヘッドモードでは無効にしてください。" >&2
    return 1
  fi

  echo "JournalHead preflight passed: ${HEAD_TABLE} は ${head_view} のストリームを持ち、${JOURNAL_TABLE} はストリームを持ちません。"
}

case "$MODE" in
  preflight)
    echo "Running JournalHead preflight..."
    preflight
    ;;

  execute)
    cat <<'EOF'
WARNING: JournalHead アイテムを作成または更新します。
         すべての書き込みノードを停止してから実行してください。
         実行後は scripts/init-read-model-checkpoints.sh で checkpoint を初期化するか、
         Journal から Read Model を再構築してください。
EOF
    echo ""
    sbt --batch "commandApi/runMain ${MIGRATION_CLASS} --execute"
    ;;

  *)
    echo "Running JournalHead migration dry-run. Nothing is written."
    echo "Use --execute to apply the migration."
    echo ""
    sbt --batch "commandApi/runMain ${MIGRATION_CLASS}"
    ;;
esac
