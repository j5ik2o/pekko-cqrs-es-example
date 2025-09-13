#!/bin/bash

set -e

# 作業ディレクトリの確認
pwd
ls -la

# 定数定義
readonly TABLES=("journal" "snapshot" "state")
readonly DEFAULT_ENDPOINT="http://localhost:8000"

# 使用方法の表示
usage() {
    echo "Usage: $0 -e <environment>"
    echo "  -e: Environment (prod or dev/local)"
    exit 1
}

# エラーハンドリング関数
handle_error() {
    echo "Error: $1" >&2
    exit 1
}

# 環境変数の設定
setup_aws_env() {
    export AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID:-x}
    export AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY:-x}
    export AWS_PAGER=""
}

# テーブル作成関数
create_table() {
    local table_name=$1
    local endpoint_url=$2
    local endpoint_param=""

    if [ -n "$endpoint_url" ]; then
        endpoint_param="--endpoint-url $endpoint_url"
    fi

    echo "Creating $table_name table..."
    echo "Current directory: $(pwd)"
    echo "Table JSON content:"
    cat "./${table_name}-table.json"
    echo "Running command: aws dynamodb create-table $endpoint_param --cli-input-json file:./${table_name}-table.json"
    # JSONファイルの内容を変数に保存
    local json_content
    json_content=$(cat "./${table_name}-table.json")
    
    echo "Endpoint parameter: [$endpoint_param]"
    echo "JSON content: $json_content"
    
    if ! aws dynamodb create-table \
        $endpoint_param \
        --cli-input-json "$json_content"; then
        handle_error "Failed to create $table_name table"
    fi
}

# メイン処理
main() {
    local env_name=""

    echo 'Sleeping for 3 seconds to allow DynamoDB to stabilize...'
    sleep 3

    # パラメーターの解析
    while getopts "e:" opt; do
        case $opt in
            e) env_name="$OPTARG" ;;
            *) usage ;;
        esac
    done

    # 必須パラメーターのチェック
    if [ -z "$env_name" ]; then
        handle_error "Environment parameter (-e) is required"
    fi

    # AWS環境変数の設定
    setup_aws_env

    # 環境に応じたエンドポイントの設定
    local endpoint=""
    if [ "$env_name" != "prod" ]; then
        endpoint=${DYNAMODB_ENDPOINT:-$DEFAULT_ENDPOINT}
        echo "Using endpoint: $endpoint"
    fi

    # テーブルの作成
    for table in "${TABLES[@]}"; do
        create_table "$table" "$endpoint"
    done
    
    echo 'Sleeping for 3 seconds to allow DynamoDB to stabilize...'
    sleep 3

    echo "All tables created successfully"
}

# メイン処理の実行
main "$@"
