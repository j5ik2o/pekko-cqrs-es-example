FROM --platform=$BUILDPLATFORM amazonlinux:2

# AWS CLIとその他必要なパッケージのインストール（アーキテクチャ自動検出）
RUN yum update -y && \
    yum install -y unzip curl python3 python3-pip && \
    pip3 install awscli && \
    yum clean all

# 作業ディレクトリの作成と設定
WORKDIR /setup

# ファイルのコピー
COPY *.json ./
COPY *.sh ./

# 実行権限の付与
RUN chmod +x *.sh

ENTRYPOINT ["./create-table.sh"]
