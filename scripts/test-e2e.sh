#!/bin/bash

# End-to-End Test: Create UserAccount via GraphQL and Query via GraphQL
# このスクリプトは、GraphQL Mutation経由でユーザーアカウントを作成し、GraphQL Query経由で取得するE2Eテストを実行します

set -e

# 設定
# 可変の待機/リトライ（環境変数で上書き可能）
E2E_MAX_RETRIES="${E2E_MAX_RETRIES:-10}"
E2E_RETRY_DELAY="${E2E_RETRY_DELAY:-3}"
E2E_WAIT_AFTER_CREATE="${E2E_WAIT_AFTER_CREATE:-8}"

COMMAND_API_HOST="${COMMAND_API_HOST:-localhost}"
COMMAND_API_PORT="${COMMAND_API_PORT:-50501}"
COMMAND_API_ENDPOINT="http://$COMMAND_API_HOST:$COMMAND_API_PORT/api/graphql"

QUERY_API_HOST="${QUERY_API_HOST:-localhost}"
QUERY_API_PORT="${QUERY_API_PORT:-50502}"
QUERY_API_ENDPOINT="http://$QUERY_API_HOST:$QUERY_API_PORT/api/graphql"

# テストデータ生成用のタイムスタンプ
TIMESTAMP=$(date +%s)
TEST_FIRST_NAME="太郎${TIMESTAMP}"
TEST_LAST_NAME="テスト"
TEST_EMAIL="test${TIMESTAMP}@example.com"

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

# GraphQL クエリを実行する関数
execute_graphql() {
    local endpoint="$1"
    local query="$2"
    local variables="${3:-{}}"

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
        "$endpoint"
}

# ヘルスチェック
health_check() {
    print_header "Health Check"

    # Command API Health Check
    print_info "Checking Command API (GraphQL) health..."
    RESPONSE=$(curl -s -w "\n%{http_code}" "$COMMAND_API_ENDPOINT")
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)

    if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "400" ]; then
        print_success "Command API is healthy"
    else
        print_error "Command API health check failed (HTTP $HTTP_CODE)"
        exit 1
    fi

    # Query API Health Check
    print_info "Checking Query API (GraphQL) health..."
    RESPONSE=$(curl -s -w "\n%{http_code}" "$QUERY_API_ENDPOINT")
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)

    if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "400" ]; then
        print_success "Query API is healthy"
    else
        print_error "Query API health check failed (HTTP $HTTP_CODE)"
        exit 1
    fi
}

# Step 1: Create UserAccount via GraphQL Mutation
create_user_account_via_graphql() {
    print_header "Step 1: Create UserAccount via GraphQL Mutation"
    print_info "Creating user account with the following details:"
    echo "  - First Name: $TEST_FIRST_NAME"
    echo "  - Last Name: $TEST_LAST_NAME"
    echo "  - Email: $TEST_EMAIL"
    echo ""

    # CreateUserAccount Mutationを実行
    local mutation='mutation CreateUserAccount($input: CreateUserAccountInput!) {
        createUserAccount(input: $input) {
            id
        }
    }'

    local variables="{
        \"input\": {
            \"firstName\": \"$TEST_FIRST_NAME\",
            \"lastName\": \"$TEST_LAST_NAME\",
            \"emailAddress\": \"$TEST_EMAIL\"
        }
    }"

    RESPONSE=$(execute_graphql "$COMMAND_API_ENDPOINT" "$mutation" "$variables")

    echo "$RESPONSE"

    # レスポンスの確認
    if echo "$RESPONSE" | jq -e '.data.createUserAccount.id' > /dev/null 2>&1; then
        CREATED_USER_ID=$(echo "$RESPONSE" | jq -r '.data.createUserAccount.id')
        print_success "UserAccount created successfully!"
        print_info "Created UserAccount ID: $CREATED_USER_ID"
        return 0
    elif echo "$RESPONSE" | jq -e '.errors' > /dev/null 2>&1; then
        ERROR_MSG=$(echo "$RESPONSE" | jq -r '.errors[0].message')
        if echo "$ERROR_MSG" | grep -q -i "already exists"; then
            print_error "UserAccount with email $TEST_EMAIL already exists"
            return 1
        else
            print_error "Failed to create user account: $ERROR_MSG"
            return 1
        fi
    else
        print_error "Unexpected response"
        return 1
    fi
}

# Step 2: Wait for eventual consistency
wait_for_consistency() {
    print_header "Step 2: Wait for Event Processing"
    print_info "Waiting for the JournalHead stream to process and update PostgreSQL..."

    # Lambda関数がイベントを処理するまで待機
    local wait_time=$E2E_WAIT_AFTER_CREATE
    for i in $(seq $wait_time -1 1); do
        echo -ne "\r  Waiting... $i seconds remaining"
        sleep 1
    done
    echo -e "\r  Waiting... Done!                    "
    print_success "Event processing time elapsed"
}

# Step 3: Query UserAccount via GraphQL
query_user_account_via_graphql() {
    print_header "Step 3: Query UserAccount via GraphQL"

    # 3.1: Query all user accounts
    print_info "Querying all user accounts to find created user..."

    local query='{
        getUserAccounts {
            id
            firstName
            lastName
            fullName
            createdAt
            updatedAt
        }
    }'

    RESPONSE=$(execute_graphql "$QUERY_API_ENDPOINT" "$query")

    if echo "$RESPONSE" | jq -e '.data.getUserAccounts' > /dev/null 2>&1; then
        # テストユーザーが含まれているか確認
        USER_DATA=$(echo "$RESPONSE" | jq ".data.getUserAccounts[] | select(.firstName == \"$TEST_FIRST_NAME\" and .lastName == \"$TEST_LAST_NAME\")")

        if [ -n "$USER_DATA" ] && [ "$USER_DATA" != "null" ]; then
            print_success "UserAccount found via GraphQL!"
            print_json "$USER_DATA"

            # データの検証
            QUERIED_FIRST_NAME=$(echo "$USER_DATA" | jq -r '.firstName')
            QUERIED_LAST_NAME=$(echo "$USER_DATA" | jq -r '.lastName')
            QUERIED_USER_ID=$(echo "$USER_DATA" | jq -r '.id')

            if [ "$QUERIED_FIRST_NAME" = "$TEST_FIRST_NAME" ] && [ "$QUERIED_LAST_NAME" = "$TEST_LAST_NAME" ] && [ "$QUERIED_USER_ID" = "$CREATED_USER_ID" ]; then
                print_success "User data matches: $QUERIED_FIRST_NAME $QUERIED_LAST_NAME"

                # 3.2: Query by ID
                print_info "Verifying user can be queried by ID: $QUERIED_USER_ID"

                local id_query='query GetUserAccount($id: String!) {
                    getUserAccount(userAccountId: $id) {
                        id
                        firstName
                        lastName
                        fullName
                        createdAt
                        updatedAt
                    }
                }'

                local id_variables="{\"id\": \"$QUERIED_USER_ID\"}"

                ID_RESPONSE=$(execute_graphql "$QUERY_API_ENDPOINT" "$id_query" "$id_variables")

                if echo "$ID_RESPONSE" | jq -e --arg id "$CREATED_USER_ID" --arg first "$TEST_FIRST_NAME" --arg last "$TEST_LAST_NAME" \
                    '.errors == null and .data.getUserAccount.id == $id and .data.getUserAccount.firstName == $first and .data.getUserAccount.lastName == $last' > /dev/null 2>&1; then
                    print_success "UserAccount successfully queried by ID"
                else
                    print_error "Failed to query UserAccount by ID"
                    return 1
                fi
            else
                print_error "User data mismatch! Expected: $CREATED_USER_ID $TEST_FIRST_NAME $TEST_LAST_NAME"
                return 1
            fi
        else
            print_error "UserAccount not found in database"
            print_info "The event might not have been processed yet"
            return 1
        fi
    else
        print_error "GraphQL query failed"
        print_json "$RESPONSE"
        return 1
    fi
}

# Step 4: Verify data consistency
verify_data_consistency() {
    print_header "Step 4: Data Consistency Verification"

    print_info "Verifying total user account count..."

    local count_query='{
        getUserAccounts {
            id
        }
    }'

    RESPONSE=$(execute_graphql "$QUERY_API_ENDPOINT" "$count_query")

    if echo "$RESPONSE" | jq -e '.data.getUserAccounts' > /dev/null 2>&1; then
        TOTAL_COUNT=$(echo "$RESPONSE" | jq '.data.getUserAccounts | length')
        print_success "Total user account count: $TOTAL_COUNT"
    fi
}

# メイン処理
main() {
    print_header "End-to-End Test Suite for UserAccount"
    print_info "Testing flow: GraphQL Mutation → Event Processing → GraphQL Query"
    print_info "Test ID: $TIMESTAMP"
    echo ""

    # ヘルスチェック
    health_check

    # E2Eテストの実行
    if create_user_account_via_graphql; then
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

            if query_user_account_via_graphql; then
                SUCCESS=true
                verify_data_consistency
            else
                RETRY_COUNT=$((RETRY_COUNT + 1))
            fi
        done

        if [ "$SUCCESS" = false ]; then
            print_error "Failed to query user account after $MAX_RETRIES retries"
            print_info "Possible causes:"
            echo "  - Lambda function not deployed or not running"
            echo "  - JournalHead stream not configured"
            echo "  - Database connection issues"
            exit 1
        fi
    else
        print_error "Failed to create user account, aborting test"
        exit 1
    fi

    print_header "Test Summary"
    print_success "End-to-End test completed successfully!"
    print_info "UserAccount ($TEST_FIRST_NAME $TEST_LAST_NAME) was created via GraphQL and retrieved successfully"
    echo ""
}

# スクリプト実行
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    main "$@"
fi
