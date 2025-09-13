#!/bin/bash
# ========================================
# クラスターモード起動スクリプト（3ノード）
# ========================================
# 使い方:
#   ./run-cluster.sh [up]   # クラスターを起動（デフォルト）
#   ./run-cluster.sh down   # クラスターを停止
#   ./run-cluster.sh logs   # サービスのログを表示
#   ./run-cluster.sh -h     # ヘルプ表示

set -e

# 共通関数の読み込み
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/run-common.sh"

# ========================================
# クラスター用環境変数設定
# ========================================
setup_cluster_environment() {
    # 基本環境変数設定
    setup_environment
    
    # クラスターノード設定
    export CLUSTER_NODE1_API_PORT="${CLUSTER_NODE1_API_PORT:-50501}"
    export CLUSTER_NODE1_MANAGEMENT_PORT="${CLUSTER_NODE1_MANAGEMENT_PORT:-8558}"
    export CLUSTER_NODE1_REMOTE_PORT="${CLUSTER_NODE1_REMOTE_PORT:-2551}"
    
    export CLUSTER_NODE2_API_PORT="${CLUSTER_NODE2_API_PORT:-50511}"
    export CLUSTER_NODE2_MANAGEMENT_PORT="${CLUSTER_NODE2_MANAGEMENT_PORT:-8559}"
    export CLUSTER_NODE2_REMOTE_PORT="${CLUSTER_NODE2_REMOTE_PORT:-2552}"
    
    export CLUSTER_NODE3_API_PORT="${CLUSTER_NODE3_API_PORT:-50521}"
    export CLUSTER_NODE3_MANAGEMENT_PORT="${CLUSTER_NODE3_MANAGEMENT_PORT:-8560}"
    export CLUSTER_NODE3_REMOTE_PORT="${CLUSTER_NODE3_REMOTE_PORT:-2553}"
}

# ========================================
# ヘルプ表示
# ========================================
show_help() {
    echo "Usage: $0 [COMMAND] [OPTIONS]"
    echo ""
    echo "Commands:"
    echo "  up         Start cluster (default)"
    echo "  down       Stop and remove containers"
    echo "  logs       Show service logs"
    echo ""
    echo "Options:"
    echo "  --attach     Run containers in foreground (default: background)"
    echo "  --no-deploy  Do not auto-deploy LocalStack Lambda"
    echo "  --deploy     Force auto-deploy (default)"
    echo "  -h, --help   Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0           # Start cluster in background"
    echo "  $0 up        # Start cluster in background (default)"
    echo "  $0 up --attach # Start cluster in foreground"
    echo "  $0 down      # Stop cluster"
    echo "  $0 logs      # Show cluster logs"
    echo "  $0 logs -f   # Follow cluster logs"
    echo ""
    echo "Environment variables:"
    echo "  【共通設定】"
    echo "  DOCKER_LOCALSTACK_PORT (default: 50503)"
    echo "  DOCKER_DYNAMODB_ADMIN_PORT (default: 50505)"
    echo "  DOCKER_POSTGRES_PORT (default: 50504)"
    echo "  DOCKER_QUERY_API_PORT (default: 50502)"
    echo ""
    echo "  【クラスター設定】"
    echo "  CLUSTER_NODE1_API_PORT (default: 50501)"
    echo "  CLUSTER_NODE1_MANAGEMENT_PORT (default: 8558)"
    echo "  CLUSTER_NODE2_API_PORT (default: 50511)"
    echo "  CLUSTER_NODE2_MANAGEMENT_PORT (default: 8559)"
    echo "  CLUSTER_NODE3_API_PORT (default: 50521)"
    echo "  CLUSTER_NODE3_MANAGEMENT_PORT (default: 8560)"
    exit 0
}

# ========================================
# クラスターノードのヘルスチェック
# ========================================
check_cluster_nodes() {
    local compose_files="$1"
    
    # ノード設定を配列で管理
    declare -a API_PORTS=("$CLUSTER_NODE1_API_PORT" "$CLUSTER_NODE2_API_PORT" "$CLUSTER_NODE3_API_PORT")
    declare -a MGMT_PORTS=("$CLUSTER_NODE1_MANAGEMENT_PORT" "$CLUSTER_NODE2_MANAGEMENT_PORT" "$CLUSTER_NODE3_MANAGEMENT_PORT")

    for i in 0 1 2; do
        node_num=$((i + 1))
        api_port="${API_PORTS[$i]}"
        mgmt_port="${MGMT_PORTS[$i]}"
        
        echo ""
        echo "Node $node_num (API: $api_port, Management: $mgmt_port):"
        
        # コンテナが実行中か確認
        if ! docker compose -f docker-compose-common.yml -f docker-compose-cluster.yml ps command-api-${node_num} | grep -q "Up\|running"; then
            echo "  ❌ Node ${node_num} container is not running!"
            echo "  📜 Showing recent logs:"
            docker compose -f docker-compose-common.yml -f docker-compose-cluster.yml logs --tail=50 command-api-${node_num}
            exit 1
        fi
        
        # HTTPヘルスチェック
        if ! wait_for_http "Node ${node_num} HTTP" "http://localhost:${api_port}/health" "Healthy" 120; then
            echo "❌ Node ${node_num} Command API failed to start within 120 seconds"
            echo "📜 Showing recent logs for node ${node_num}:"
            docker compose $compose_files logs --tail=50 command-api-${node_num}
            exit 1
        fi
        
        # Pekko Management確認
        if ! wait_for_http "Node ${node_num} Cluster" "http://localhost:${mgmt_port}/cluster/members" "Up" 120; then
            echo "❌ Node ${node_num} Cluster Management failed to start within 120 seconds"
            echo "📜 Showing recent logs for node ${node_num}:"
            docker compose $compose_files logs --tail=50 command-api-${node_num}
            exit 1
        fi
    done
}

# ========================================
# クラスターアクセスポイント表示
# ========================================
show_cluster_access_points() {
    echo "📍 Access points:"
    echo "  - Command Node 1: http://localhost:${CLUSTER_NODE1_API_PORT}"
    echo "    Command Health: http://localhost:${CLUSTER_NODE1_API_PORT}/health"
    echo "    Command Management: http://localhost:${CLUSTER_NODE1_MANAGEMENT_PORT}"
    echo ""
    echo "  - Command Node 2: http://localhost:${CLUSTER_NODE2_API_PORT}"
    echo "    Command Health: http://localhost:${CLUSTER_NODE2_API_PORT}/health"
    echo "    Command Management: http://localhost:${CLUSTER_NODE2_MANAGEMENT_PORT}"
    echo ""
    echo "  - Command Node 3: http://localhost:${CLUSTER_NODE3_API_PORT}"
    echo "    Command Health: http://localhost:${CLUSTER_NODE3_API_PORT}/health"
    echo "    Command Management: http://localhost:${CLUSTER_NODE3_MANAGEMENT_PORT}"
    echo ""
    echo "📊 Cluster Management UI:"
    echo "  - http://localhost:${CLUSTER_NODE1_MANAGEMENT_PORT}/cluster/members"
    echo ""
}

# ========================================
# クラスター起動処理
# ========================================
start_cluster_services() {
    echo "🌐 Starting Command API Cluster (3 nodes)..."
    echo ""

    prepare_docker_environment "-f docker-compose-common.yml -f docker-compose-cluster.yml"

    # クラスターの起動
    echo "🚀 Starting cluster nodes..."
    if [ "$ATTACH_MODE" = true ]; then
        echo "📎 Running in foreground mode (Ctrl+C to stop)..."
        docker compose -f docker-compose-common.yml -f docker-compose-cluster.yml up "$@"
    else
        docker compose -f docker-compose-common.yml -f docker-compose-cluster.yml up $DETACHED "$@"

        # ヘルスチェック
        echo "⏳ Waiting for cluster to be ready..."
        sleep 10

        # 各ノードの状態を確認
        echo "📊 Checking cluster status..."
        check_cluster_nodes "-f docker-compose-common.yml -f docker-compose-cluster.yml"

        # Query API の状態確認
        check_query_api "-f docker-compose-common.yml -f docker-compose-cluster.yml"

        post_startup_tasks "-f docker-compose-common.yml -f docker-compose-cluster.yml"

        # アクセスポイント表示
        show_cluster_access_points
        show_common_access_points
    fi
}

# ========================================
# メイン処理
# ========================================
main() {
    # デフォルト設定
    setup_defaults
    
    # 引数処理（--db-onlyはクラスターモードでは無効）
    DB_ONLY=false
    parse_arguments "$@"
    
    # 環境変数設定
    setup_cluster_environment

    # ヘルプ表示
    if [ "$SHOW_HELP" = true ]; then
        show_help
    fi

    # コマンド処理
    case "$COMMAND" in
        down)
            handle_down_command dummy -f docker-compose-common.yml -f docker-compose-cluster.yml
            ;;

        logs)
            handle_logs_command dummy -f docker-compose-common.yml -f docker-compose-cluster.yml
            ;;

        up)
            start_cluster_services
            ;;
    esac
}

# スクリプト実行
main "$@"