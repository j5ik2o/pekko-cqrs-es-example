#!/bin/bash

# ===== 共通ライブラリ =====
# Dockerビルドスクリプトで使用する共通関数と変数

# 色付き出力用の定義
export RED='\033[0;31m'
export GREEN='\033[0;32m'
export YELLOW='\033[1;33m'
export BLUE='\033[0;34m'
export NC='\033[0m' # No Color

# 印刷関数
print_header() {
    echo -e "\n${BLUE}=== $1 ===${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

# エラーハンドリング
check_command() {
    local cmd=$1
    local install_msg=$2
    
    if ! command -v "$cmd" &> /dev/null; then
        print_error "エラー: $cmd が見つかりません"
        [ -n "$install_msg" ] && echo "$install_msg"
        exit 1
    fi
}

# プロジェクトルートへの移動
goto_project_root() {
    # スクリプトから2階層上（scripts/lib -> プロジェクトルート）
    cd "$(dirname "${BASH_SOURCE[1]}")/.." || exit 1
}

# sbtビルド実行
run_sbt_build() {
    local task=$1
    local description=$2
    
    print_info "$description"
    echo "これには数分かかる場合があります..."
    
    if sbt "$task"; then
        print_success "$description が完了しました"
        return 0
    else
        print_error "$description に失敗しました"
        return 1
    fi
}

# Dockerイメージの確認と表示
show_docker_images() {
    local image_patterns=("$@")
    local grep_pattern="REPOSITORY"
    
    for pattern in "${image_patterns[@]}"; do
        grep_pattern="$grep_pattern|$pattern"
    done
    
    echo ""
    echo "作成されたイメージ:"
    docker images | grep -E "$grep_pattern" | head -$(( ${#image_patterns[@]} + 1 ))
}

# 共通ヘルプメッセージ
show_common_help() {
    local script_name=$1
    local additional_help=$2
    
    echo "使用方法: $script_name [オプション]"
    echo ""
    echo "オプション:"
    echo "  -c, --clean    クリーンビルド（sbt cleanを先に実行）"
    echo "  -h, --help     このヘルプを表示"
    
    if [ -n "$additional_help" ]; then
        echo ""
        echo "$additional_help"
    fi
    
    echo ""
    echo "例:"
    echo "  $script_name              # 通常のビルド"
    echo "  $script_name --clean      # クリーンビルド"
}

# オプション解析
parse_docker_build_options() {
    CLEAN_BUILD=false
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            -c|--clean)
                CLEAN_BUILD=true
                shift
                ;;
            -h|--help)
                return 1  # ヘルプを表示するために1を返す
                ;;
            *)
                print_error "エラー: 不明なオプション '$1'"
                return 1
                ;;
        esac
    done
    
    return 0
}

# クリーンビルドの実行
perform_clean_if_needed() {
    if [ "$CLEAN_BUILD" = true ]; then
        print_info "プロジェクトをクリーンしています..."
        sbt clean
    fi
}