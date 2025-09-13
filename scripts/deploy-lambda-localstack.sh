#!/bin/bash

# LocalStack Lambda デプロイスクリプト
# read-model-updater を LocalStack Lambda にデプロイします

set -e

# 強制再作成オプションの確認
FORCE_RECREATE=false
if [ "$1" = "--force" ] || [ "$1" = "-f" ]; then
    FORCE_RECREATE=true
    echo "⚠️  強制再作成モードが有効です"
fi

# 環境変数の設定
export AWS_ACCESS_KEY_ID=dummy
export AWS_SECRET_ACCESS_KEY=dummy
export AWS_DEFAULT_REGION=ap-northeast-1
export PORT=50503
export HOST=localhost
export ENDPOINT_URL=http://${HOST}:${PORT}

# Lambda関数名
FUNCTION_NAME="pcqrses-read-model-updater"

# DynamoDB テーブル名とストリームARN
TABLE_NAME="Journal"

# アーキテクチャの自動検出
if [ "$(uname -m)" = "arm64" ] || [ "$(uname -m)" = "aarch64" ]; then
    LAMBDA_ARCH="arm64"
    echo "🔧 検出されたアーキテクチャ: ARM64"
else
    LAMBDA_ARCH="x86_64"
    echo "🔧 検出されたアーキテクチャ: x86_64"
fi

# 環境変数でオーバーライド可能
if [ -n "${LAMBDA_ARCHITECTURE}" ]; then
    LAMBDA_ARCH="${LAMBDA_ARCHITECTURE}"
    echo "⚙️  環境変数によるアーキテクチャ指定: ${LAMBDA_ARCH}"
fi

echo "🚀 LocalStack Lambda デプロイを開始します..."

# プロジェクトルートに移動
cd "$(dirname "$0")/.."

echo "📦 read-model-updater をビルド中..."
# sbt assembly で fat JAR を作成（バッチモードで実行、ページャーを無効化）
export PAGER=cat
sbt --batch "project readModelUpdater" assembly

# Assembly JARのパス
ASSEMBLY_JAR_PATH="apps/read-model-updater/target/scala-3.6.2/read-model-updater-lambda.jar"

# Assembly JARが存在することを確認
if [ ! -f "$ASSEMBLY_JAR_PATH" ]; then
    echo "❌ エラー: Assembly JAR が見つかりません: $ASSEMBLY_JAR_PATH"
    exit 1
fi

echo "✅ Assembly JAR が作成されました: $ASSEMBLY_JAR_PATH"

# DynamoDB ストリーム ARN を取得
echo "🔍 DynamoDB ストリーム ARN を取得中..."
STREAM_ARN=$(aws dynamodb describe-table \
    --endpoint-url $ENDPOINT_URL \
    --table-name $TABLE_NAME \
    --query 'Table.LatestStreamArn' \
    --output text)

if [ "$STREAM_ARN" = "None" ] || [ -z "$STREAM_ARN" ]; then
    echo "❌ エラー: DynamoDB テーブル '$TABLE_NAME' のストリームが見つかりません"
    echo "   テーブルにストリームが有効化されていることを確認してください"
    exit 1
fi

echo "✅ ストリーム ARN: $STREAM_ARN"

# LambdaのActive化を待機する関数（最大60秒）
wait_for_lambda_active() {
  local fn_name="$1"
  local timeout=60
  local waited=0
  echo "⏳ Lambda関数のActive化を待機中... ($fn_name)"
  while true; do
    state=$(aws lambda get-function \
      --endpoint-url $ENDPOINT_URL \
      --function-name "$fn_name" \
      --query 'Configuration.State' \
      --output text 2>/dev/null || echo "")
    if [ "$state" = "Active" ]; then
      echo "✅ Lambda関数がActiveになりました: $fn_name"
      break
    fi
    if [ "$state" = "Failed" ]; then
      echo "❌ Lambda関数がFailed状態です: $fn_name"
      exit 1
    fi
    sleep 2
    waited=$((waited+2))
    if [ $waited -ge $timeout ]; then
      echo "⚠️  タイムアウト: Lambda関数がActiveになりませんでした ($fn_name, state=$state)"
      break
    fi
  done
}

# Lambda関数の存在確認
echo "🔧 Lambda関数の存在確認中..."
if aws lambda get-function \
    --endpoint-url $ENDPOINT_URL \
    --function-name $FUNCTION_NAME \
    --query 'Configuration.FunctionName' \
    --output text 2>/dev/null; then
    FUNCTION_EXISTS="true"
else
    FUNCTION_EXISTS="false"
fi

# 環境変数をファイルに書き出し
ENV_JSON_FILE="/tmp/lambda-env.json"
cat > $ENV_JSON_FILE << 'EOF'
{
  "Variables": {
    "DATABASE_URL": "jdbc:postgresql://postgres:5432/pcqrses_development",
    "DATABASE_USER": "postgres",
    "DATABASE_PASSWORD": "postgres",
    "AWS_DEFAULT_REGION": "ap-northeast-1",
    "DYNAMODB_ENDPOINT_URI": "http://localstack:4566",
    "KINESIS_ENDPOINT_URI": "http://localstack:4566",
    "CLOUDWATCH_ENDPOINT_URI": "http://localstack:4566"
  }
}
EOF

if [ "$FUNCTION_EXISTS" = "true" ]; then
    if [ "$FORCE_RECREATE" = "true" ]; then
        echo "🗑️  既存のLambda関数を削除中..."
        aws lambda delete-function \
            --endpoint-url $ENDPOINT_URL \
            --function-name $FUNCTION_NAME

        echo "🆕 新しいLambda関数を作成中..."
        aws lambda create-function \
            --endpoint-url $ENDPOINT_URL \
            --function-name $FUNCTION_NAME \
            --runtime java17 \
            --handler io.github.j5ik2o.pcqrses.readmodelupdater.LambdaHandler \
            --role arn:aws:iam::000000000000:role/lambda-role \
            --zip-file fileb://$ASSEMBLY_JAR_PATH \
            --timeout 300 \
            --memory-size 512 \
            --architectures $LAMBDA_ARCH \
            --environment file://$ENV_JSON_FILE
        # Active化を待機
        wait_for_lambda_active "$FUNCTION_NAME"
    else
        echo "🔄 既存のLambda関数を更新中..."
        # 関数の設定を更新
        aws lambda update-function-configuration \
            --endpoint-url $ENDPOINT_URL \
            --function-name $FUNCTION_NAME \
            --runtime java17 \
            --handler io.github.j5ik2o.pcqrses.readmodelupdater.LambdaHandler \
            --timeout 300 \
            --memory-size 512 \
            --environment file://$ENV_JSON_FILE

        # 設定の更新を待つ
        sleep 2

        # 関数のコードを更新
        aws lambda update-function-code \
            --endpoint-url $ENDPOINT_URL \
            --function-name $FUNCTION_NAME \
            --zip-file fileb://$ASSEMBLY_JAR_PATH
        # Active化を待機
        wait_for_lambda_active "$FUNCTION_NAME"
    fi
else
    echo "🆕 新しいLambda関数を作成中..."
    aws lambda create-function \
        --endpoint-url $ENDPOINT_URL \
        --function-name $FUNCTION_NAME \
        --runtime java17 \
        --handler io.github.j5ik2o.pcqrses.readmodelupdater.LambdaHandler \
        --role arn:aws:iam::000000000000:role/lambda-role \
        --zip-file fileb://$ASSEMBLY_JAR_PATH \
        --timeout 300 \
        --memory-size 512 \
        --architectures $LAMBDA_ARCH \
        --environment file://$ENV_JSON_FILE
    # Active化を待機
    wait_for_lambda_active "$FUNCTION_NAME"
fi

# 一時ファイルを削除
rm $ENV_JSON_FILE

# DynamoDB ストリームのイベントソースマッピングを作成
echo "🔗 DynamoDB ストリームのイベントソースマッピングを作成中..."

# 既存のイベントソースマッピングを確認して削除
echo "📋 既存のイベントソースマッピングを確認中..."
EXISTING_UUIDS=$(aws lambda list-event-source-mappings \
    --endpoint-url $ENDPOINT_URL \
    --function-name $FUNCTION_NAME \
    --query 'EventSourceMappings[?EventSourceArn==`'$STREAM_ARN'`].UUID' \
    --output text 2>/dev/null)

MAPPING_DELETED=false
if [ -n "$EXISTING_UUIDS" ] && [ "$EXISTING_UUIDS" != "None" ]; then
    for uuid in $EXISTING_UUIDS; do
        echo "🗑️  既存のイベントソースマッピング $uuid を削除中..."
        # 削除コマンドを実行（出力を表示してエラーを確認）
        if aws lambda delete-event-source-mapping \
            --endpoint-url $ENDPOINT_URL \
            --uuid $uuid 2>&1 | grep -v "ResourceNotFoundException"; then
            echo "   ✓ 削除リクエスト送信完了: $uuid"
            MAPPING_DELETED=true
        fi
    done

    # 削除処理が完了するまで待機
    if [ "$MAPPING_DELETED" = "true" ]; then
        echo "⏳ イベントソースマッピングの削除完了を待機中..."

        # 削除が完了したか確認（最大30秒待機）
        RETRIES=0
        while [ $RETRIES -lt 15 ]; do
            sleep 2

            # 該当するストリームARNのマッピングが存在するか確認
            ALL_MAPPINGS=$(aws lambda list-event-source-mappings \
                --endpoint-url $ENDPOINT_URL \
                --function-name $FUNCTION_NAME \
                --query 'EventSourceMappings[?EventSourceArn==`'$STREAM_ARN'`].[UUID, State]' \
                --output text 2>/dev/null)

            if [ -z "$ALL_MAPPINGS" ] || [ "$ALL_MAPPINGS" = "None" ]; then
                echo "✅ 既存のマッピングが削除されました"
                break
            fi

            # まだ存在する場合は状態を表示
            echo "   待機中... ($((RETRIES+1))/15) - 状態: $ALL_MAPPINGS"
            RETRIES=$((RETRIES+1))
        done

        # タイムアウト時の処理
        if [ $RETRIES -eq 15 ]; then
            echo "⚠️  警告: マッピング削除のタイムアウト"
            echo "   強制的に再試行します..."

            # 残っているマッピングを強制削除
            REMAINING_UUIDS=$(aws lambda list-event-source-mappings \
                --endpoint-url $ENDPOINT_URL \
                --function-name $FUNCTION_NAME \
                --query 'EventSourceMappings[?EventSourceArn==`'$STREAM_ARN'`].UUID' \
                --output text 2>/dev/null)

            if [ -n "$REMAINING_UUIDS" ] && [ "$REMAINING_UUIDS" != "None" ]; then
                for uuid in $REMAINING_UUIDS; do
                    echo "   🔄 強制削除: $uuid"
                    aws lambda delete-event-source-mapping \
                        --endpoint-url $ENDPOINT_URL \
                        --uuid $uuid --force 2>/dev/null || true
                done
                sleep 5
            fi
        fi
    fi
else
    echo "✅ 既存のイベントソースマッピングなし"
fi

# 新しいイベントソースマッピングを作成（LambdaがActiveであることを前提）
echo "📝 新しいイベントソースマッピングを作成中..."
aws lambda create-event-source-mapping \
    --endpoint-url $ENDPOINT_URL \
    --function-name $FUNCTION_NAME \
    --event-source-arn $STREAM_ARN \
    --starting-position LATEST \
    --batch-size 10 \
    --maximum-batching-window-in-seconds 5

# Assembly JARはそのまま残す（デバッグ用に保持）
echo "🧹 デプロイメント完了"

echo ""
echo "✅ Lambda関数のデプロイが完了しました!"
echo "   関数名: $FUNCTION_NAME"
echo "   ハンドラー: io.github.j5ik2o.pcqrses.readmodelupdater.LambdaHandler"
echo "   ストリーム ARN: $STREAM_ARN"
echo ""
echo "📋 確認用コマンド:"
echo "   aws lambda list-functions --endpoint-url $ENDPOINT_URL --query 'Functions[?FunctionName==\`$FUNCTION_NAME\`]'"
echo "   aws lambda list-event-source-mappings --endpoint-url $ENDPOINT_URL --function-name $FUNCTION_NAME"
echo ""
echo "🧪 テスト用コマンド:"
echo "   aws lambda invoke --endpoint-url $ENDPOINT_URL --function-name $FUNCTION_NAME --payload '{}' response.json"
echo ""

# 最終確認: 実際にデプロイされた関数を表示
echo "🔍 最終確認: デプロイされたLambda関数"
aws lambda list-functions \
  --endpoint-url $ENDPOINT_URL \
  --query "Functions[?FunctionName=='$FUNCTION_NAME'].{Name:FunctionName,State:State,Runtime:Runtime}" \
  --output table 2>/dev/null || echo "  ⚠️  関数の確認に失敗しました"
