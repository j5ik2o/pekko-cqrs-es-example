#!/bin/bash

# LocalStack Lambda クリーンアップスクリプト
# read-model-updater Lambda関数とイベントソースマッピングを削除します

set -e

# 環境変数の設定
export AWS_ACCESS_KEY_ID=dummy
export AWS_SECRET_ACCESS_KEY=dummy
export AWS_DEFAULT_REGION=ap-northeast-1
export ENDPOINT_URL=http://localhost:4566

# Lambda関数名
FUNCTION_NAME="pcqrses-read-model-updater"

echo "🧹 LocalStack Lambda クリーンアップを開始します..."

# イベントソースマッピングを削除
echo "🔗 イベントソースマッピングを削除中..."
aws lambda list-event-source-mappings \
    --endpoint-url $ENDPOINT_URL \
    --function-name $FUNCTION_NAME \
    --query 'EventSourceMappings[].UUID' \
    --output text | while read uuid; do
    if [ -n "$uuid" ] && [ "$uuid" != "None" ]; then
        echo "🗑️  イベントソースマッピング $uuid を削除中..."
        aws lambda delete-event-source-mapping \
            --endpoint-url $ENDPOINT_URL \
            --uuid $uuid
    fi
done

# Lambda関数を削除
echo "🗑️  Lambda関数 $FUNCTION_NAME を削除中..."
aws lambda delete-function \
    --endpoint-url $ENDPOINT_URL \
    --function-name $FUNCTION_NAME \
    2>/dev/null \
    && echo "✅ Lambda関数を削除しました" \
    || echo "⚠️  Lambda関数が見つかりませんでした（既に削除済みの可能性があります）"

echo ""
echo "✅ クリーンアップが完了しました!"