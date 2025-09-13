#!/bin/bash
# ========================================
# 単一ノード構成起動スクリプト
# ========================================
# 使い方:
#   ./run-single.sh [up]      # 全サービスをDockerで起動（デフォルト）
#   ./run-single.sh down      # 全サービスを停止
#   ./run-single.sh logs      # サービスのログを表示
#   ./run-single.sh --db-only # データベースのみ起動（デバッグ用）
#   ./run-single.sh -h        # ヘルプ表示

set -e

# 共通関数の読み込み
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/run-common.sh"

# ========================================
# ヘルプ表示
# ========================================
show_help() {
    echo "Usage: $0 [COMMAND] [OPTIONS]"
    echo ""
    echo "Commands:"
    echo "  up         Start services (default)"
    echo "  down       Stop and remove containers"
    echo "  logs       Show service logs"
    echo ""
    echo "Options:"
    echo "  --attach     Run containers in foreground (default: background)"
    echo "  --db-only    Run only database services in Docker (for debugging)"
    echo "  --no-deploy  Do not auto-deploy LocalStack Lambda"
    echo "  --deploy     Force auto-deploy (default)"
    echo "  -h, --help   Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0           # Start all services in background"
    echo "  $0 up        # Start all services in background (default)"
    echo "  $0 up --attach # Start all services in foreground"
    echo "  $0 down      # Stop all services"
    echo "  $0 logs      # Show service logs"
    echo "  $0 logs -f   # Follow service logs"
    echo "  $0 --db-only # Run only DBs, start APIs from IDE"
    echo ""
    echo "Environment variables:"
    echo "  DOCKER_COMMAND_API_PORT (default: 50501)"
    echo "  DOCKER_QUERY_API_PORT (default: 50502)"
    echo "  DOCKER_POSTGRES_PORT (default: 50504)"
    echo "  DOCKER_LOCALSTACK_PORT (default: 50503)"
    echo "  DOCKER_DYNAMODB_ADMIN_PORT (default: 50505)"
    exit 0
}

# ========================================
# Command API起動（単一ノードモード）
# ========================================
start_single_node_services() {
    echo "🐳 Starting Development Environment with Docker..."
    echo "   (All services run in containers)"
    echo ""

    prepare_docker_environment "-f docker-compose-common.yml -f docker-compose-local.yml"

    # 開発環境の起動
    echo "🚀 Starting services..."
    if [ "$ATTACH_MODE" = true ]; then
        echo "📎 Running in foreground mode (Ctrl+C to stop)..."
        docker compose -f docker-compose-common.yml -f docker-compose-local.yml up "$@"
    else
        docker compose -f docker-compose-common.yml -f docker-compose-local.yml up $DETACHED "$@"

        # ヘルスチェック
        echo "⏳ Waiting for services to be ready..."
        sleep 5

        # Command API の状態確認
        echo "📊 Checking services status..."
        if ! wait_for_http "Command API" "http://localhost:${DOCKER_COMMAND_API_PORT}/health" "Healthy" 120; then
            echo "❌ Command API failed to start within 120 seconds"
            echo "📜 Showing recent logs:"
            docker compose -f docker-compose-common.yml -f docker-compose-local.yml logs --tail=50 command-api
            exit 1
        fi

        # Query API の状態確認
        check_query_api "-f docker-compose-common.yml -f docker-compose-local.yml"
        
        # DynamoDB Admin UI の確認（オプション）
        check_dynamodb_admin

        post_startup_tasks "-f docker-compose-common.yml -f docker-compose-local.yml"

        # アクセスポイント表示
        echo "📍 Access points:"
        echo "  - Command API: http://localhost:${DOCKER_COMMAND_API_PORT}"
        echo "  - Command Health Check: http://localhost:${DOCKER_COMMAND_API_PORT}/health"
        show_common_access_points
    fi
}

# ========================================
# メイン処理
# ========================================
main() {
    # デフォルト設定
    setup_defaults
    
    # 引数処理
    parse_arguments "$@"
    
    # 環境変数設定
    setup_environment

    # ヘルプ表示
    if [ "$SHOW_HELP" = true ]; then
        show_help
    fi

    # コマンド処理
    case "$COMMAND" in
        down)
            if [ "$DB_ONLY" = true ]; then
                handle_down_command dummy -f docker-compose-common.yml
            else
                handle_down_command dummy -f docker-compose-common.yml -f docker-compose-local.yml
            fi
            ;;

        logs)
            if [ "$DB_ONLY" = true ]; then
                handle_logs_command dummy -f docker-compose-common.yml
            else
                handle_logs_command dummy -f docker-compose-common.yml -f docker-compose-local.yml
            fi
            ;;

        up)
            if [ "$DB_ONLY" = true ]; then
                run_db_only_mode
            else
                start_single_node_services
            fi
            ;;
    esac
}

# スクリプト実行
main "$@"