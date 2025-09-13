#!/bin/bash

# End-to-End Test: Create Staff via gRPC and Query via GraphQL
# このスクリプトは、gRPC経由でスタッフを作成し、GraphQL経由で取得するE2Eテストを実行します

set -e

# 設定
# 可変の待機/リトライ（環境変数で上書き可能）
E2E_MAX_RETRIES="${E2E_MAX_RETRIES:-10}"
E2E_RETRY_DELAY="${E2E_RETRY_DELAY:-3}"
E2E_WAIT_AFTER_CREATE="${E2E_WAIT_AFTER_CREATE:-8}"

GRPC_HOST="${GRPC_HOST:-localhost}"
GRPC_PORT="${GRPC_PORT:-50501}"
GRAPHQL_HOST="${GRAPHQL_HOST:-localhost}"
GRAPHQL_PORT="${GRAPHQL_PORT:-50502}"
GRAPHQL_ENDPOINT="http://$GRAPHQL_HOST:$GRAPHQL_PORT/graphql"
GRPCURL_IMAGE="fullstorydev/grpcurl:latest"

# テストデータ生成用のタイムスタンプ
TIMESTAMP=$(date +%s)
TEST_STAFF_NO="TEST${TIMESTAMP}"
TEST_GIVEN_NAME="太郎"
TEST_FAMILY_NAME="テスト"
TEST_EMAIL="test${TIMESTAMP}@example.com"
TEST_BIRTHDAY="1990-01-15"
TEST_HIRED_DATE="2024-04-01"

# 色付き出力用の関数
print_header() {
    echo -e "\n\033[1;34m=== $1 ===\033[0m"
}

print_success() {
    echo -e "\033[1;32m✓ $1\033[0m"
}

print_error() {
    echo -e "\033[1;31m✗ $1\033[0m"
}

print_info() {
    echo -e "\033[1;33mℹ $1\033[0m"
}

print_json() {
    echo "$1" | jq '.' 2>/dev/null || echo "$1"
}

# grpcurlコマンドを実行する関数
run_grpcurl() {
    docker run --rm \
        --network host \
        $GRPCURL_IMAGE \
        -plaintext \
        "$@"
}

# GraphQL クエリを実行する関数
execute_graphql() {
    local query="$1"
    local variables="${2:-{}}"

    # クエリ内の改行をスペースに置換
    query=$(echo "$query" | tr '\n' ' ' | sed 's/  */ /g')

    # jqでJSONペイロードを作成
    local payload
    if [ -z "$variables" ] || [ "$variables" = "{}" ]; then
        payload=$(jq -n --arg q "$query" '{query: $q}')
    else
        payload=$(echo "$variables" | jq --arg q "$query" '{query: $q, variables: .}' 2>/dev/null)
        if [ -z "$payload" ]; then
            payload="{\"query\": \"$query\", \"variables\": $variables}"
        fi
    fi

    # curlで実行
    curl -s -X POST \
        -H "Content-Type: application/json" \
        -d "$payload" \
        "$GRAPHQL_ENDPOINT"
}

# ヘルスチェック
health_check() {
    print_header "Health Check"
    
    # gRPC Health Check
    print_info "Checking Command API (gRPC) health..."
    if run_grpcurl $GRPC_HOST:$GRPC_PORT list > /dev/null 2>&1; then
        print_success "Command API is healthy"
    else
        print_error "Command API is not responding"
        exit 1
    fi
    
    # GraphQL Health Check
    print_info "Checking Query API (GraphQL) health..."
    RESPONSE=$(curl -s -w "\n%{http_code}" "http://$GRAPHQL_HOST:$GRAPHQL_PORT/health")
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    
    if [ "$HTTP_CODE" = "200" ]; then
        print_success "Query API is healthy"
    else
        print_error "Query API health check failed (HTTP $HTTP_CODE)"
        exit 1
    fi
}

# Step 1: Create Staff via gRPC
create_staff_via_grpc() {
    print_header "Step 1: Create Staff via gRPC"
    print_info "Creating staff with the following details:"
    echo "  - Staff No: $TEST_STAFF_NO"
    echo "  - Name: $TEST_FAMILY_NAME $TEST_GIVEN_NAME"
    echo "  - Email: $TEST_EMAIL"
    echo "  - Birthday: $TEST_BIRTHDAY"
    echo "  - Hired Date: $TEST_HIRED_DATE"
    echo ""
    
    # CreateStaffリクエストを送信
    RESPONSE=$(run_grpcurl \
        -format text \
        -d "staff_no:\"$TEST_STAFF_NO\" given_name:\"$TEST_GIVEN_NAME\" family_name:\"$TEST_FAMILY_NAME\" email:\"$TEST_EMAIL\" birthday:\"$TEST_BIRTHDAY\" hired_date:\"$TEST_HIRED_DATE\"" \
        $GRPC_HOST:$GRPC_PORT \
        io.github.j5ik2o.pcqrses.proto.staff.StaffService/CreateStaff 2>&1) || true
    
    echo "$RESPONSE"
    
    # レスポンスの確認
    if echo "$RESPONSE" | grep -q "ERROR"; then
        if echo "$RESPONSE" | grep -q "AlreadyExists"; then
            print_error "Staff with StaffNo $TEST_STAFF_NO already exists"
            print_info "This might be from a previous test run. Continuing with query test..."
            return 0
        else
            print_error "Failed to create staff"
            return 1
        fi
    elif echo "$RESPONSE" | grep -q "staff_id:"; then
        # スタッフIDを抽出
        CREATED_STAFF_ID=$(echo "$RESPONSE" | grep -o 'staff_id: *"[^"]*"' | sed 's/.*: *"\([^"]*\)".*/\1/')
        print_success "Staff created successfully!"
        print_info "Created Staff ID: $CREATED_STAFF_ID"
        return 0
    else
        print_error "Unexpected response"
        return 1
    fi
}

# Step 2: Wait for eventual consistency
wait_for_consistency() {
    print_header "Step 2: Wait for Event Processing"
    print_info "Waiting for DynamoDB stream to process and update PostgreSQL..."
    
    # Lambda関数がイベントを処理するまで待機
    local wait_time=$E2E_WAIT_AFTER_CREATE
    for i in $(seq $wait_time -1 1); do
        echo -ne "\r  Waiting... $i seconds remaining"
        sleep 1
    done
    echo -e "\r  Waiting... Done!                    "
    print_success "Event processing time elapsed"
}

# Step 3: Query Staff via GraphQL
query_staff_via_graphql() {
    print_header "Step 3: Query Staff via GraphQL"
    
    # 3.1: Query by StaffNo
    print_info "Querying staff by StaffNo: $TEST_STAFF_NO"
    
    local query='query GetStaff($staffNo: String!) { 
        staffByNo(staffNo: $staffNo) { 
            id 
            staffNo 
            globalFamilyName 
            globalGivenName 
            localFamilyName 
            localGivenName 
            nameLocale 
            createdAt 
            updatedAt 
        } 
    }'
    
    local variables="{\"staffNo\": \"$TEST_STAFF_NO\"}"
    
    RESPONSE=$(execute_graphql "$query" "$variables")
    
    if echo "$RESPONSE" | jq -e '.data.staffByNo' > /dev/null 2>&1; then
        STAFF_DATA=$(echo "$RESPONSE" | jq '.data.staffByNo')
        if [ "$STAFF_DATA" != "null" ]; then
            print_success "Staff found via GraphQL!"
            print_json "$RESPONSE"
            
            # データの検証
            QUERIED_STAFF_NO=$(echo "$STAFF_DATA" | jq -r '.staffNo')
            if [ "$QUERIED_STAFF_NO" = "$TEST_STAFF_NO" ]; then
                print_success "Staff No matches: $QUERIED_STAFF_NO"
            else
                print_error "Staff No mismatch! Expected: $TEST_STAFF_NO, Got: $QUERIED_STAFF_NO"
            fi
        else
            print_error "Staff not found in database"
            print_info "The event might not have been processed yet"
            return 1
        fi
    else
        print_error "GraphQL query failed"
        print_json "$RESPONSE"
        return 1
    fi
    
    # 3.2: Query all staff to verify the new staff is in the list
    print_info "Verifying staff appears in allStaff query..."
    
    local all_query='{ 
        allStaff { 
            staffNo 
            globalFamilyName 
            globalGivenName 
        } 
    }'
    
    RESPONSE=$(execute_graphql "$all_query")
    
    if echo "$RESPONSE" | jq -e '.data.allStaff' > /dev/null 2>&1; then
        # テストスタッフが含まれているか確認
        if echo "$RESPONSE" | jq ".data.allStaff[] | select(.staffNo == \"$TEST_STAFF_NO\")" > /dev/null 2>&1; then
            print_success "Staff appears in allStaff query"
            TOTAL_COUNT=$(echo "$RESPONSE" | jq '.data.allStaff | length')
            print_info "Total staff count: $TOTAL_COUNT"
        else
            print_error "Staff not found in allStaff query"
        fi
    fi
}

# Step 4: Verify data consistency
verify_data_consistency() {
    print_header "Step 4: Data Consistency Verification"
    
    # 検索クエリでも確認
    print_info "Searching staff by family name..."
    
    local search_query='query SearchStaff($familyName: String) { 
        searchStaff(globalFamilyName: $familyName) { 
            staffNo 
            globalFamilyName 
            globalGivenName 
        } 
    }'
    
    local variables="{\"familyName\": \"$TEST_FAMILY_NAME\"}"
    
    RESPONSE=$(execute_graphql "$search_query" "$variables")
    
    if echo "$RESPONSE" | jq -e '.data.searchStaff' > /dev/null 2>&1; then
        if echo "$RESPONSE" | jq ".data.searchStaff[] | select(.staffNo == \"$TEST_STAFF_NO\")" > /dev/null 2>&1; then
            print_success "Staff found via search query"
        else
            print_error "Staff not found via search query"
        fi
    fi
}

# オプション: クリーンアップ（退職処理）
cleanup_test_staff() {
    print_header "Optional: Cleanup Test Data"
    print_info "Scheduling retirement for test staff..."
    
    # 明日の日付を退職日として設定
    RETIREMENT_DATE=$(date -v+1d +%Y-%m-%d 2>/dev/null || date -d "+1 day" +%Y-%m-%d)
    
    RESPONSE=$(run_grpcurl \
        -format text \
        -d "staff_no:\"$TEST_STAFF_NO\" retirement_date:\"$RETIREMENT_DATE\"" \
        $GRPC_HOST:$GRPC_PORT \
        io.github.j5ik2o.pcqrses.proto.staff.StaffService/ScheduleRetirementStaff 2>&1) || true
    
    echo "$RESPONSE"
    
    if echo "$RESPONSE" | grep -q "staff_id:"; then
        print_success "Test staff retirement scheduled for $RETIREMENT_DATE"
    else
        print_info "Could not schedule retirement (this is optional)"
    fi
}

# メイン処理
main() {
    print_header "End-to-End Test Suite"
    print_info "Testing flow: gRPC Create → Event Processing → GraphQL Query"
    print_info "Test ID: $TIMESTAMP"
    echo ""
    
    # ヘルスチェック
    health_check
    
    # E2Eテストの実行
    if create_staff_via_grpc; then
        wait_for_consistency
        
        # リトライロジック付きでクエリを実行
        MAX_RETRIES=$E2E_MAX_RETRIES
        RETRY_COUNT=0
        SUCCESS=false
        
        while [ $RETRY_COUNT -lt $MAX_RETRIES ] && [ "$SUCCESS" = false ]; do
            if [ $RETRY_COUNT -gt 0 ]; then
                print_info "Retry attempt $RETRY_COUNT/$MAX_RETRIES... (sleep ${E2E_RETRY_DELAY}s)"
                sleep "$E2E_RETRY_DELAY"
            fi
            
            if query_staff_via_graphql; then
                SUCCESS=true
                verify_data_consistency
            else
                RETRY_COUNT=$((RETRY_COUNT + 1))
            fi
        done
        
        if [ "$SUCCESS" = false ]; then
            print_error "Failed to query staff after $MAX_RETRIES retries"
            print_info "Possible causes:"
            echo "  - Lambda function not deployed or not running"
            echo "  - DynamoDB streams not configured"
            echo "  - Database connection issues"
            exit 1
        fi
    else
        print_error "Failed to create staff, aborting test"
        exit 1
    fi
    
    # オプション: クリーンアップ
    if [ "${CLEANUP:-false}" = "true" ]; then
        cleanup_test_staff
    fi
    
    print_header "Test Summary"
    print_success "End-to-End test completed successfully!"
    print_info "Staff No: $TEST_STAFF_NO was created via gRPC and retrieved via GraphQL"
    echo ""
    print_info "To run cleanup: CLEANUP=true $0"
}

# スクリプト実行
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    main "$@"
fi
