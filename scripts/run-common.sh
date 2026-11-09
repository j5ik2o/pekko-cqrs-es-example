#!/bin/bash
# ========================================
# 共通起動スクリプト関数
# ========================================
# run-single.sh と run-cluster.sh で共通利用される関数群

# 共通関数の読み込み
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/common.sh"

# ========================================
# デフォルト設定
# ========================================
setup_defaults() {
    COMMAND="${COMMAND:-up}"
    DB_ONLY="${DB_ONLY:-false}"
    SHOW_HELP="${SHOW_HELP:-false}"
    DETACHED="${DETACHED:--d}"
    ATTACH_MODE="${ATTACH_MODE:-false}"
    AUTO_DEPLOY="${AUTO_DEPLOY:-true}"
    DOWN_VOLUMES="${DOWN_VOLUMES:-false}"
}

# ========================================
# 環境変数設定
# ========================================
setup_environment() {
    export DOCKER_COMMAND_API_PORT="${DOCKER_COMMAND_API_PORT:-50501}"
    export DOCKER_QUERY_API_PORT="${DOCKER_QUERY_API_PORT:-50502}"
    export DOCKER_POSTGRES_PORT="${DOCKER_POSTGRES_PORT:-50504}"
    export DOCKER_LOCALSTACK_PORT="${DOCKER_LOCALSTACK_PORT:-50503}"
    export DOCKER_DYNAMODB_ADMIN_PORT="${DOCKER_DYNAMODB_ADMIN_PORT:-50505}"
    export DOCKER_PGADMIN_PORT="${DOCKER_PGADMIN_PORT:-50506}"
}

# ========================================
# 引数処理
# ========================================
parse_arguments() {
    # 第一引数がコマンドの場合は取得
    if [[ "$1" =~ ^(up|down|logs)$ ]]; then
        COMMAND=$1
        shift
    fi

    # その他の引数処理
    while [[ "$#" -gt 0 ]]; do
        case $1 in
            --attach) DETACHED=""; ATTACH_MODE=true; shift ;;
            --db-only) DB_ONLY=true; shift ;;
            --no-deploy) AUTO_DEPLOY=false; shift ;;
            --deploy) AUTO_DEPLOY=true; shift ;;
            --volumes) DOWN_VOLUMES=true; shift ;;
            -h|--help) SHOW_HELP=true; shift ;;
            *)
                # logsコマンドの場合は追加の引数を許可
                if [ "$COMMAND" = "logs" ]; then
                    break
                else
                    echo "Unknown option: $1"
                    exit 1
                fi
                ;;
        esac
    done
}

# ========================================
# downコマンド処理
# ========================================
handle_down_command() {
    echo "🛑 Stopping services..."
    shift  # 最初の引数（compose_files）を除去
    if [ "$DOWN_VOLUMES" = true ]; then
        docker compose "$@" down -v
    else
        docker compose "$@" down
    fi
    echo "✅ Services stopped"
    exit 0
}

# ========================================
# logsコマンド処理
# ========================================
handle_logs_command() {
    echo "📜 Showing service logs..."
    shift  # 最初の引数（compose_files）を除去
    docker compose "$@" logs
    exit 0
}

# ========================================
# DB_ONLYモード実行
# ========================================
run_db_only_mode() {
    echo "🗄️  Starting Database Services Only..."
    echo "   (DBs run in Docker, APIs will be started separately)"
    echo ""

    # 既存のコンテナを停止・削除
    echo "🧹 Cleaning up existing containers..."
    docker compose -f docker-compose-common.yml down

    # データベースサービスのみ起動
    echo "🚀 Starting database services..."
    if [ "$ATTACH_MODE" = true ]; then
        echo "📎 Running in foreground mode (Ctrl+C to stop)..."
        docker compose -f docker-compose-common.yml up "$@" localstack dynamodb-setup dynamodb-admin postgres flyway
    else
        docker compose -f docker-compose-common.yml up $DETACHED "$@" localstack dynamodb-setup dynamodb-admin postgres flyway

        echo "⏳ Waiting for services to be ready..."
        sleep 10

        # サービスの状態確認
        check_db_services

        echo ""
        echo "🎉 Database services are running!"
        echo ""
        show_manual_start_instructions

        # Lambda自動デプロイ（--db-onlyモードではスキップ）
        # deploy_lambda_if_enabled

        echo ""
        echo "🛑 To stop databases: $0 down"
        echo ""
        show_db_only_access_points
    fi
}

# ========================================
# データベースサービスの状態確認
# ========================================
check_db_services() {
    echo "📊 Checking services status..."

    # LocalStackの確認
    if curl -s "http://localhost:${DOCKER_LOCALSTACK_PORT}/_localstack/health" | grep -q "running"; then
        echo "  ✅ LocalStack is running"
    else
        echo "  ❌ LocalStack is not responding"
    fi

    # PostgreSQLの確認
    if pg_isready -h localhost -p ${DOCKER_POSTGRES_PORT} > /dev/null 2>&1; then
        echo "  ✅ PostgreSQL is ready"
    else
        echo "  ⚠️  PostgreSQL is not responding"
    fi

    # DynamoDB Adminの確認
    if curl -s "http://localhost:${DOCKER_DYNAMODB_ADMIN_PORT}" > /dev/null 2>&1; then
        echo "  ✅ DynamoDB Admin UI is available"
    else
        echo "  ⚠️  DynamoDB Admin UI is not responding"
    fi
}

# ========================================
# 手動起動手順の表示
# ========================================
show_manual_start_instructions() {
    echo "🔧 To start APIs manually:"
    echo "  1. Command API:"
    echo "     - From IntelliJ: Run 'io.github.j5ik2o.pcqrses.commandapi.Main'"
    echo "     - From sbt: sbt \"commandApi/run\""
    echo "  2. Query API:"
    echo "     - From IntelliJ: Run 'io.github.j5ik2o.pcqrses.queryapi.Main'"
    echo "     - From sbt: sbt \"queryApi/run\""
    echo ""
    echo "💡 Debug configuration for IntelliJ:"
    echo "  - Set these environment variables:"
    echo "    J5IK2O_DYNAMO_DB_JOURNAL_DYNAMO_DB_CLIENT_ENDPOINT=http://localhost:${DOCKER_LOCALSTACK_PORT}"
    echo "    J5IK2O_DYNAMO_DB_SNAPSHOT_DYNAMO_DB_CLIENT_ENDPOINT=http://localhost:${DOCKER_LOCALSTACK_PORT}"
    echo "    J5IK2O_DYNAMO_DB_STATE_DYNAMO_DB_CLIENT_ENDPOINT=http://localhost:${DOCKER_LOCALSTACK_PORT}"
    echo "    AWS_REGION=ap-northeast-1"
    echo "    AWS_ACCESS_KEY_ID=dummy"
    echo "    AWS_SECRET_ACCESS_KEY=dummy"
    echo "    PEKKO_CLUSTER_ENABLED=false"
}

# ========================================
# Lambda デプロイ処理
# ========================================
deploy_lambda_if_enabled() {
    if [ "$AUTO_DEPLOY" = true ]; then
        echo ""
        echo "🪄 Deploying Lambda to LocalStack..."
        export PORT="$DOCKER_LOCALSTACK_PORT"
        tries=0; max_tries=10
        until ./scripts/deploy-lambda-localstack.sh; do
            tries=$((tries+1))
            if [ $tries -ge $max_tries ]; then
                echo "❌ Lambda deploy failed after ${max_tries} attempts" >&2
                break
            fi
            echo "⏳ Retry deploy in 3s... ($tries/$max_tries)"
            sleep 3
        done

        # Lambda関数の状態確認
        echo ""
        echo "⏳ Waiting for Lambda to be fully registered..."
        sleep 5
        echo "📊 Checking Lambda function status..."
        if ! check_lambda_status "pcqrses-read-model-updater" "http://localhost:${DOCKER_LOCALSTACK_PORT}"; then
            echo "⚠️  Lambda function may not be ready yet, but continuing..."
        fi
    else
        echo "ℹ️  Skipping Lambda auto-deploy (--no-deploy)"
    fi
}

# ========================================
# DB_ONLYモードのアクセスポイント表示
# ========================================
show_db_only_access_points() {
    echo "📍 Access points:"
    echo "  - PostgreSQL: localhost:${DOCKER_POSTGRES_PORT}"
    echo "  - DynamoDB Admin: http://localhost:${DOCKER_DYNAMODB_ADMIN_PORT}"
    echo "  - pgAdmin: http://localhost:${DOCKER_PGADMIN_PORT}"
    echo "  - LocalStack: http://localhost:${DOCKER_LOCALSTACK_PORT}"
    echo ""
    echo "🔄 Read Model Updater (Lambda):"
    echo "  - Function: pcqrses-read-model-updater"
    echo "  - Trigger: JournalHead DynamoDB Stream (automatic)"
    echo "  - Check status: aws lambda get-function --endpoint-url http://localhost:${DOCKER_LOCALSTACK_PORT} --function-name pcqrses-read-model-updater"
}

# ========================================
# Query API のヘルスチェック
# ========================================
check_query_api() {
    echo ""
    echo "📊 Checking Query API status..."
    if ! wait_for_http "Query API" "http://localhost:${DOCKER_QUERY_API_PORT}/api/health" "healthy" 120; then
        echo "❌ Query API failed to start within 120 seconds"
        echo "📜 Showing recent logs:"
        docker compose $1 logs --tail=50 query-api
        exit 1
    fi
}

# ========================================
# DynamoDB Admin UI のチェック（オプション）
# ========================================
check_dynamodb_admin() {
    if ! wait_for_http "DynamoDB Admin UI" "http://localhost:${DOCKER_DYNAMODB_ADMIN_PORT}" ".*" 30; then
        echo "  ⚠️  DynamoDB Admin UI is not available (optional service)"
    fi
}

# ========================================
# 共通アクセスポイント表示
# ========================================
show_common_access_points() {
    echo "📍 Other services:"
    echo "  - Query GraphQL API: http://localhost:${DOCKER_QUERY_API_PORT}/api/graphql"
    echo "  - Query Health Check: http://localhost:${DOCKER_QUERY_API_PORT}/api/health"
    echo "  - Query GraphQL Playground: http://localhost:${DOCKER_QUERY_API_PORT}/api/graphql (ブラウザで開く)"
    echo "  - DynamoDB Admin: http://localhost:${DOCKER_DYNAMODB_ADMIN_PORT}"
    echo "  - pgAdmin: http://localhost:${DOCKER_PGADMIN_PORT}"
    echo "  - PostgreSQL: localhost:${DOCKER_POSTGRES_PORT}"
    echo "  - LocalStack: http://localhost:${DOCKER_LOCALSTACK_PORT}"
    echo ""
    echo "🔄 Read Model Updater (Lambda):"
    echo "  - Function: pcqrses-read-model-updater"
    echo "  - Trigger: JournalHead DynamoDB Stream (automatic)"
    echo "  - Check status: aws lambda get-function --endpoint-url http://localhost:${DOCKER_LOCALSTACK_PORT} --function-name pcqrses-read-model-updater"
}

# ========================================
# Dockerイメージビルドと環境準備
# ========================================
prepare_docker_environment() {
    local compose_files="$1"

    # Dockerイメージのビルド
    echo "🏗️  Building Docker images..."
    sbt dockerBuildAll

    # 既存のコンテナを停止・削除
    echo "🧹 Cleaning up existing containers..."
    docker compose $compose_files down

    # DynamoDB のテーブル定義 (journal-head-table.json など) の変更を反映する
    echo "🧱 Rebuilding DynamoDB setup image..."
    docker compose $compose_files build dynamodb-setup
}

# ========================================
# サービス起動後の共通処理
# ========================================
post_startup_tasks() {
    local compose_files="$1"

    echo ""
    echo "🎉 Services are running!"

    # Lambda自動デプロイ
    deploy_lambda_if_enabled

    echo ""
    echo "🛑 To stop: $0 down"
    echo ""
}
