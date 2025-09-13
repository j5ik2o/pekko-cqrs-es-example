#!/bin/bash

# GraphQL エンドポイントをテストするスクリプト

set -e

# 設定
GRAPHQL_HOST="${GRAPHQL_HOST:-localhost}"
GRAPHQL_PORT="${GRAPHQL_PORT:-50502}"
GRAPHQL_ENDPOINT="http://$GRAPHQL_HOST:$GRAPHQL_PORT/graphql"

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
    local query="$1"
    local variables="${2:-{}}"

    # クエリ内の改行をスペースに置換
    query=$(echo "$query" | tr '\n' ' ' | sed 's/  */ /g')

    # jqでJSONペイロードを作成
    local payload
    if [ -z "$variables" ] || [ "$variables" = "{}" ]; then
        payload=$(jq -n --arg q "$query" '{query: $q}')
    else
        # variablesをstdinから読み込み、queryと結合（エラーを抑制）
        payload=$(echo "$variables" | jq --arg q "$query" '{query: $q, variables: .}' 2>/dev/null)
        # エラーが発生した場合のフォールバック
        if [ -z "$payload" ]; then
            # 直接JSON文字列を構築
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
    print_info "Checking Query API health..."

    RESPONSE=$(curl -s -w "\n%{http_code}" "http://$GRAPHQL_HOST:$GRAPHQL_PORT/health")
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    BODY=$(echo "$RESPONSE" | sed '$d')

    if [ "$HTTP_CODE" = "200" ]; then
        print_success "Query API is healthy: $BODY"
    else
        print_error "Query API health check failed (HTTP $HTTP_CODE)"
        exit 1
    fi
}

# GraphiQL の確認
check_graphiql() {
    print_header "GraphiQL Interface Check"
    print_info "Checking if GraphiQL is available..."

    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$GRAPHQL_ENDPOINT")

    if [ "$HTTP_CODE" = "200" ]; then
        print_success "GraphiQL is available at $GRAPHQL_ENDPOINT"
    elif [ "$HTTP_CODE" = "404" ]; then
        print_info "GraphiQL is disabled (production mode)"
    else
        print_error "Unexpected response (HTTP $HTTP_CODE)"
    fi
}

# イントロスペクションクエリ
introspection_query() {
    print_header "Schema Introspection"
    print_info "Fetching GraphQL schema..."

    local query='
    {
        __schema {
            types {
                name
                kind
                description
            }
        }
    }'

    RESPONSE=$(execute_graphql "$query")

    if echo "$RESPONSE" | jq -e '.data.__schema.types' > /dev/null 2>&1; then
        print_success "Schema introspection successful"
        echo "$RESPONSE" | jq '.data.__schema.types[] | select(.name | startswith("Staff"))' 2>/dev/null
    else
        print_error "Schema introspection failed"
        print_json "$RESPONSE"
    fi
}

# 利用可能なクエリの一覧取得
list_queries() {
    print_header "Available Queries"
    print_info "Listing all available queries..."

    local query='
    {
        __schema {
            queryType {
                fields {
                    name
                    description
                    args {
                        name
                        type {
                            name
                            kind
                        }
                    }
                }
            }
        }
    }'

    RESPONSE=$(execute_graphql "$query")

    if echo "$RESPONSE" | jq -e '.data.__schema.queryType.fields' > /dev/null 2>&1; then
        print_success "Query list retrieved"
        echo "$RESPONSE" | jq '.data.__schema.queryType.fields[] | {name: .name, description: .description}'
    else
        print_error "Failed to retrieve query list"
        print_json "$RESPONSE"
    fi
}

# 全スタッフ取得クエリ
test_all_staff() {
    print_header "Test: Get All Staff"
    print_info "Executing allStaff query..."

    local query='
    {
        allStaff {
            id
            staffNo
            globalFamilyName
            globalMiddleName
            globalGivenName
            localFamilyName
            localMiddleName
            localGivenName
            nameLocale
            createdAt
            updatedAt
        }
    }'

    RESPONSE=$(execute_graphql "$query")

    if echo "$RESPONSE" | jq -e '.data.allStaff' > /dev/null 2>&1; then
        COUNT=$(echo "$RESPONSE" | jq '.data.allStaff | length')
        print_success "Query executed successfully (Found $COUNT staff members)"
        echo "$RESPONSE" | jq '.data.allStaff[:2]' 2>/dev/null  # 最初の2件だけ表示
    else
        if echo "$RESPONSE" | jq -e '.errors' > /dev/null 2>&1; then
            print_error "Query failed with errors:"
            echo "$RESPONSE" | jq '.errors'
        else
            print_info "No staff data found (empty result)"
            print_json "$RESPONSE"
        fi
    fi
}

# 特定スタッフ取得クエリ
test_staff_by_no() {
    print_header "Test: Get Staff by Staff Number"
    print_info "Executing staff query with staffNo parameter..."

    local query='query GetStaff($staffNo: String!) { staffByNo(staffNo: $staffNo) { id staffNo globalFamilyName globalGivenName localFamilyName localGivenName nameLocale } }'

    local variables='{"staffNo": "STF001"}'

    # デバッグ: execute_graphql呼び出し前に確認
    # echo "DEBUG Query: $query" >&2
    # echo "DEBUG Variables: $variables" >&2
    
    RESPONSE=$(execute_graphql "$query" "$variables")

    if echo "$RESPONSE" | jq -e '.data.staffByNo' > /dev/null 2>&1; then
        if [ "$(echo "$RESPONSE" | jq '.data.staffByNo')" != "null" ]; then
            print_success "Staff found:"
            print_json "$RESPONSE"
        else
            print_info "No staff found with staffNo: STF001"
        fi
    else
        print_error "Query failed"
        print_json "$RESPONSE"
    fi
}

# ロケールによるスタッフ検索
test_staff_by_locale() {
    print_header "Test: Get Staff by Locale"
    print_info "Executing staffByLocale query..."

    local query='query GetStaffByLocale($locale: String) { staffByLocale(nameLocale: $locale) { staffNo nameLocale globalFamilyName globalGivenName } }'

    local variables='{"locale": "ja_JP"}'

    RESPONSE=$(execute_graphql "$query" "$variables")

    if echo "$RESPONSE" | jq -e '.data.staffByLocale' > /dev/null 2>&1; then
        COUNT=$(echo "$RESPONSE" | jq '.data.staffByLocale | length')
        print_success "Query executed successfully (Found $COUNT staff members with locale ja_JP)"
    else
        print_error "Query failed"
        print_json "$RESPONSE"
    fi
}

# 名前検索テスト
test_search_staff() {
    print_header "Test: Search Staff by Name"
    print_info "Executing searchStaff query..."

    local query='query SearchStaff($globalFamily: String, $localFamily: String) { searchStaff(globalFamilyName: $globalFamily, localFamilyName: $localFamily) { staffNo globalFamilyName globalGivenName localFamilyName localGivenName } }'

    local variables='{"globalFamily": "Yamada"}'

    RESPONSE=$(execute_graphql "$query" "$variables")

    if echo "$RESPONSE" | jq -e '.data.searchStaff' > /dev/null 2>&1; then
        COUNT=$(echo "$RESPONSE" | jq '.data.searchStaff | length')
        print_success "Search executed successfully (Found $COUNT matches)"
        if [ "$COUNT" -gt 0 ]; then
            echo "$RESPONSE" | jq '.data.searchStaff[:2]' 2>/dev/null
        fi
    else
        print_error "Search failed"
        print_json "$RESPONSE"
    fi
}

# バッチクエリテスト（複数クエリの同時実行）
test_batch_query() {
    print_header "Test: Batch Query"
    print_info "Executing multiple queries in single request..."

    local query='
    {
        allStaffCount: allStaff {
            staffNo
        }
        japaneseStaff: staffByLocale(nameLocale: "ja_JP") {
            staffNo
            nameLocale
        }
        searchYamada: searchStaff(globalFamilyName: "Yamada") {
            staffNo
            globalFamilyName
        }
    }'

    RESPONSE=$(execute_graphql "$query")

    if echo "$RESPONSE" | jq -e '.data' > /dev/null 2>&1; then
        print_success "Batch query executed successfully"
        echo "Results summary:"
        echo "  - All staff count: $(echo "$RESPONSE" | jq '.data.allStaffCount | length')"
        echo "  - Japanese staff: $(echo "$RESPONSE" | jq '.data.japaneseStaff | length')"
        echo "  - Search results: $(echo "$RESPONSE" | jq '.data.searchYamada | length')"
    else
        print_error "Batch query failed"
        print_json "$RESPONSE"
    fi
}

# フラグメント使用のテスト
test_with_fragments() {
    print_header "Test: Query with Fragments"
    print_info "Testing GraphQL fragments..."

    local query='
    fragment StaffBasicInfo on Staff {
        staffNo
        nameLocale
    }

    fragment StaffNameInfo on Staff {
        globalFamilyName
        globalGivenName
        localFamilyName
        localGivenName
    }

    {
        allStaff {
            ...StaffBasicInfo
            ...StaffNameInfo
            createdAt
        }
    }'

    RESPONSE=$(execute_graphql "$query")

    if echo "$RESPONSE" | jq -e '.data.allStaff' > /dev/null 2>&1; then
        print_success "Fragment query executed successfully"
    else
        print_error "Fragment query failed"
        print_json "$RESPONSE"
    fi
}

# エラーハンドリングテスト
test_error_handling() {
    print_header "Test: Error Handling"
    print_info "Testing error responses..."

    # 1. 不正なクエリ
    print_info "Testing malformed query..."
    local bad_query='{ invalid query }'
    RESPONSE=$(execute_graphql "$bad_query")

    if echo "$RESPONSE" | jq -e '.errors' > /dev/null 2>&1; then
        print_success "Error handled correctly for malformed query"
    else
        print_error "Expected error not returned"
    fi

    # 2. 存在しないフィールド
    print_info "Testing non-existent field..."
    local invalid_field_query='
    {
        allStaff {
            staffNo
            nonExistentField
        }
    }'

    RESPONSE=$(execute_graphql "$invalid_field_query")

    if echo "$RESPONSE" | jq -e '.errors' > /dev/null 2>&1; then
        print_success "Error handled correctly for non-existent field"
    else
        print_error "Expected error not returned"
    fi
}

# パフォーマンステスト
performance_test() {
    print_header "Performance Test"
    print_info "Measuring query response times..."

    local query='{ allStaff { staffNo } }'

    # 5回実行して平均を取る
    local total_time=0
    local iterations=5

    for i in $(seq 1 $iterations); do
        START=$(date +%s%N)
        execute_graphql "$query" > /dev/null
        END=$(date +%s%N)
        ELAPSED=$((($END - $START) / 1000000))  # ミリ秒に変換
        total_time=$((total_time + ELAPSED))
        echo "  Run $i: ${ELAPSED}ms"
    done

    AVG=$((total_time / iterations))
    print_info "Average response time: ${AVG}ms"

    if [ $AVG -lt 100 ]; then
        print_success "Excellent performance (< 100ms)"
    elif [ $AVG -lt 500 ]; then
        print_success "Good performance (< 500ms)"
    else
        print_error "Poor performance (>= 500ms)"
    fi
}

# メイン処理
main() {
    print_header "GraphQL API Test Suite"
    print_info "Target: $GRAPHQL_ENDPOINT"

    # 基本的な接続確認
    health_check
    check_graphiql

    # スキーマテスト
    introspection_query
    list_queries

    # クエリテスト
    test_all_staff
    test_staff_by_no
    test_staff_by_locale
    test_search_staff

    # 高度なテスト
    test_batch_query
    test_with_fragments
    test_error_handling

    # パフォーマンステスト
    performance_test

    print_header "Test Summary"
    print_success "All GraphQL API tests completed"
    print_info "GraphiQL interface available at: $GRAPHQL_ENDPOINT"
}

# スクリプト実行（直接実行時のみ）
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    main "$@"
fi
