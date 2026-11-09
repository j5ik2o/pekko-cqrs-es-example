#!/bin/bash

# 既存環境の JournalHead マイグレーションとプリフライト。
# すべての書き込みノードを停止してから実行する。

set -euo pipefail

cd "$(dirname "$0")/.."

usage() {
  cat <<'EOF'
既存環境の JournalHead マイグレーションとプリフライト

使い方:
  ./scripts/migrate-journal-head.sh              # dry-run
  ./scripts/migrate-journal-head.sh --execute    # JournalHead アイテムを作成/更新
  ./scripts/migrate-journal-head.sh --preflight  # ストリーム設定の確認

前提:
  - すべての書き込みノードを停止していること
  - JournalHead テーブルと read_model_checkpoints テーブルを作成済みであること
  - 既存の Read Model が Journal の最新シーケンス番号まで追いついていること

接続先は環境変数で上書きできます。
  J5IK2O_DYNAMO_DB_JOURNAL_DYNAMO_DB_CLIENT_ENDPOINT
  J5IK2O_DYNAMO_DB_SNAPSHOT_DYNAMO_DB_CLIENT_ENDPOINT
  J5IK2O_DYNAMO_DB_JOURNAL_TABLE_NAME
  J5IK2O_DYNAMO_DB_JOURNAL_HEAD_TABLE_NAME
  J5IK2O_DYNAMO_DB_SNAPSHOT_TABLE_NAME

認証は AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY、AWS_PROFILE、IAM ロールを使います。
ローカル Endpoint の場合は未設定時に dummy を設定します。
EOF
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

LOCALSTACK_PORT="${DOCKER_LOCALSTACK_PORT:-50503}"
ENDPOINT="${J5IK2O_DYNAMO_DB_JOURNAL_DYNAMO_DB_CLIENT_ENDPOINT:-http://localhost:${LOCALSTACK_PORT}}"
export J5IK2O_DYNAMO_DB_JOURNAL_DYNAMO_DB_CLIENT_ENDPOINT="$ENDPOINT"
export J5IK2O_DYNAMO_DB_SNAPSHOT_DYNAMO_DB_CLIENT_ENDPOINT="${J5IK2O_DYNAMO_DB_SNAPSHOT_DYNAMO_DB_CLIENT_ENDPOINT:-http://localhost:${LOCALSTACK_PORT}}"

is_local_endpoint() {
  case "$1" in
    http://localhost:*|http://127.0.0.1:*|http://localstack:*) return 0 ;;
    *) return 1 ;;
  esac
}

# ローカルの LocalStack 以外では、IAM ロールや AWS_PROFILE を上書きしない。
if is_local_endpoint "$ENDPOINT"; then
  export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-dummy}"
  export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-dummy}"
fi

# コマンド側の設定は固定キーを持つため、環境の認証情報を設定側にも渡す。
if [ -n "${AWS_ACCESS_KEY_ID:-}" ] && [ -n "${AWS_SECRET_ACCESS_KEY:-}" ]; then
  export J5IK2O_DYNAMO_DB_JOURNAL_DYNAMO_DB_CLIENT_ACCESS_KEY_ID="${J5IK2O_DYNAMO_DB_JOURNAL_DYNAMO_DB_CLIENT_ACCESS_KEY_ID:-$AWS_ACCESS_KEY_ID}"
  export J5IK2O_DYNAMO_DB_JOURNAL_DYNAMO_DB_CLIENT_SECRET_ACCESS_KEY="${J5IK2O_DYNAMO_DB_JOURNAL_DYNAMO_DB_CLIENT_SECRET_ACCESS_KEY:-$AWS_SECRET_ACCESS_KEY}"
  export J5IK2O_DYNAMO_DB_SNAPSHOT_DYNAMO_DB_CLIENT_ACCESS_KEY_ID="${J5IK2O_DYNAMO_DB_SNAPSHOT_DYNAMO_DB_CLIENT_ACCESS_KEY_ID:-$AWS_ACCESS_KEY_ID}"
  export J5IK2O_DYNAMO_DB_SNAPSHOT_DYNAMO_DB_CLIENT_SECRET_ACCESS_KEY="${J5IK2O_DYNAMO_DB_SNAPSHOT_DYNAMO_DB_CLIENT_SECRET_ACCESS_KEY:-$AWS_SECRET_ACCESS_KEY}"
elif ! is_local_endpoint "$ENDPOINT"; then
  echo "WARNING: AWS の認証情報が未設定です。j5ik2o.conf の固定キーが使われないよう、" >&2
  echo "         J5IK2O_DYNAMO_DB_JOURNAL_DYNAMO_DB_CLIENT_ACCESS_KEY_ID と ..._SECRET_ACCESS_KEY を設定するか、" >&2
  echo "         設定ファイルから access-key-id / secret-access-key を削除してください。" >&2
fi

if [ -z "${PROTOC_PATH:-}" ] && command -v protoc >/dev/null 2>&1; then
  export PROTOC_PATH
  PROTOC_PATH="$(command -v protoc)"
fi

JOURNAL_TABLE="${J5IK2O_DYNAMO_DB_JOURNAL_TABLE_NAME:-Journal}"
HEAD_TABLE="${J5IK2O_DYNAMO_DB_JOURNAL_HEAD_TABLE_NAME:-JournalHead}"
SNAPSHOT_TABLE="${J5IK2O_DYNAMO_DB_SNAPSHOT_TABLE_NAME:-Snapshot}"
MIGRATION_CLASS="com.github.j5ik2o.pekko.persistence.dynamodb.journal.head.JournalHeadMigration"

echo "Journal : ${JOURNAL_TABLE}"
echo "JournalHead : ${HEAD_TABLE}"
echo "Snapshot : ${SNAPSHOT_TABLE}"
echo "Endpoint : ${ENDPOINT}"
echo ""

preflight() {
  local head_stream journal_stream
  local head_enabled head_view journal_enabled

  if ! head_stream=$(aws dynamodb describe-table \
    --endpoint-url "$ENDPOINT" \
    --table-name "$HEAD_TABLE" \
    --query 'Table.StreamSpecification' \
    --output json); then
    echo "JournalHead preflight failed: ${HEAD_TABLE} の情報を取得できません。" >&2
    return 1
  fi
  if ! journal_stream=$(aws dynamodb describe-table \
    --endpoint-url "$ENDPOINT" \
    --table-name "$JOURNAL_TABLE" \
    --query 'Table.StreamSpecification' \
    --output json); then
    echo "JournalHead preflight failed: ${JOURNAL_TABLE} の情報を取得できません。" >&2
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
