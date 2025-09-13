#!/bin/bash

# Common helper functions for run scripts

# Wait for an HTTP endpoint to return content matching a pattern
# Usage: wait_for_http "Name" "http://host:port/path" "Pattern" [timeoutSeconds]
wait_for_http() {
  local name="$1"; local url="$2"; local pattern="$3"; local timeout="${4:-20}"
  local waited=0
  echo "  ⏳ Waiting for ${name}..."
  while [ $waited -lt $timeout ]; do
    if curl -s "$url" | grep -q "$pattern"; then
      echo "  ✅ ${name} is ready"
      return 0
    fi
    sleep 2
    waited=$((waited+2))
  done
  echo "  ⚠️  ${name} is not responding yet"
  return 1
}

# Check if Lambda function exists and is active in LocalStack
# Usage: check_lambda_status "function-name" [endpoint-url] [max-retries]
check_lambda_status() {
  local function_name="$1"
  local endpoint="${2:-http://localhost:${DOCKER_LOCALSTACK_PORT:-50503}}"
  local max_retries="${3:-3}"
  
  echo "  🔍 Checking Lambda function: ${function_name}..."
  
  # LocalStack用のAWS認証情報を設定
  export AWS_REGION=ap-northeast-1
  export AWS_ACCESS_KEY_ID=dummy
  export AWS_SECRET_ACCESS_KEY=dummy
  
  # リトライロジック
  local retry_count=0
  local found=false
  
  while [ $retry_count -lt $max_retries ]; do
    # Lambda関数リストを取得
    local all_functions=$(aws lambda list-functions \
      --endpoint-url "$endpoint" \
      --query 'Functions[*].FunctionName' \
      --output text 2>/dev/null)
    
    if [ -n "$all_functions" ]; then
      # 関数が見つかった場合
      if echo "$all_functions" | grep -q "$function_name"; then
        found=true
        break
      fi
    fi
    
    # リトライが必要な場合
    retry_count=$((retry_count + 1))
    if [ $retry_count -lt $max_retries ]; then
      echo "  ⏳ Waiting for Lambda to register... (attempt $retry_count/$max_retries)"
      sleep 3
    fi
  done
  
  if [ "$found" = false ]; then
    # 最終的に見つからなかった場合
    if [ -z "$all_functions" ]; then
      echo "  ⚠️  No Lambda functions found in LocalStack after $max_retries attempts"
      echo "  💡 Hint: Lambda deployment may have failed or LocalStack is not ready"
    else
      echo "  ⚠️  Lambda function '${function_name}' not found after $max_retries attempts"
      echo "  📋 Available functions: ${all_functions}"
      echo "  💡 Try deploying with: ./scripts/deploy-lambda-localstack.sh"
    fi
    return 1
  fi
  
  # Lambda関数の状態確認
  local state=$(aws lambda get-function \
    --endpoint-url "$endpoint" \
    --function-name "$function_name" \
    --query 'Configuration.State' \
    --output text 2>/dev/null)
  
  if [ "$state" = "Active" ]; then
    echo "  ✅ Lambda function is Active"
    
    # イベントソースマッピングの確認
    local mappings=$(aws lambda list-event-source-mappings \
      --endpoint-url "$endpoint" \
      --function-name "$function_name" \
      --query 'EventSourceMappings[*].State' \
      --output text 2>/dev/null)
    
    if [ -n "$mappings" ]; then
      echo "  📎 Event source mappings: ${mappings}"
    else
      echo "  ⚠️  No event source mappings found"
    fi
    
    return 0
  else
    echo "  ⏳ Lambda function state: ${state}"
    return 1
  fi
}

# Check Lambda function logs in LocalStack
# Usage: check_lambda_logs "function-name" [endpoint-url] [tail-lines]
check_lambda_logs() {
  local function_name="$1"
  local endpoint="${2:-http://localhost:${DOCKER_LOCALSTACK_PORT:-50503}}"
  local tail_lines="${3:-10}"
  
  echo "  📋 Recent Lambda logs (last ${tail_lines} lines):"
  
  # LocalStackのコンテナログから Lambda関数のログを抽出
  docker logs localstack 2>&1 | grep -A2 "$function_name" | tail -n "$tail_lines" || {
    echo "  ⚠️  No recent logs found for ${function_name}"
    return 1
  }
}

