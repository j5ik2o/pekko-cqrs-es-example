# CLAUDE.md

このファイルは、このリポジトリでClaude Code (claude.ai/code) を使用する際のガイダンスを提供します。

## 注意事項

- 日本語で応対すること

## プロジェクト概要

Apache Pekko（Akkaからのフォーク）を使用したScala 3のCQRS/イベントソーシング実装例です。コマンド側とクエリ側を適切に分離したクリーンアーキテクチャを実証しています。

## ビルドシステム

このプロジェクトはSBTをビルドツールとして使用しています。バージョン情報は`project/build.properties`および`project/Dependencies.scala`を参照してください。

### 必須コマンド

```bash
# 全モジュールのコンパイル
sbt compile

# テストの実行
sbt test

# カバレッジ付きテストの実行
sbt testCoverage

# コードフォーマット
sbt fmt

# フォーマットとリントのチェック
sbt lint

# クリーンとコンパイル
sbt clean compile

# 単一テストの実行
sbt "testOnly <完全修飾クラス名>"

# 特定モジュールのテスト実行
sbt "commandDomain/test"
sbt "queryInterfaceAdapter/test"

# データベースマイグレーション（クエリ側）
sbt migrateQuery        # マイグレーション実行
sbt infoQuery          # マイグレーション情報表示
sbt validateQuery      # マイグレーション検証
sbt cleanMigrateQuery  # クリーン後マイグレーション

# Docker操作
sbt dockerBuildAll     # 全サービスのDockerイメージビルド

# DAO生成（クエリ側）
sbt "queryInterfaceAdapter/generateAllWithDb"
```

## アーキテクチャ

このシステムはCQRS（コマンドクエリ責任分離）とイベントソーシングパターンを採用したレイヤードアーキテクチャに従っています：

### モジュール構造

```
modules/
├── infrastructure/           # 共有インフラコード（シリアライゼーション、ユーティリティ）
├── command/                 # コマンド側（書き込みモデル）
│   ├── domain/              # ドメインエンティティ、値オブジェクト、集約
│   ├── use-case/            # アプリケーションサービス、コマンドハンドラ
│   ├── interface-adapter/   # Pekkoアクター、永続化、HTTP/gRPCエンドポイント
│   ├── interface-adapter-contract/  # コマンド用Protobuf定義
│   └── interface-adapter-event-serializer/  # イベントシリアライゼーション
└── query/                   # クエリ側（読み取りモデル）
    ├── interface-adapter/   # GraphQL API、Slick DAO、プロジェクション
    └── flyway-migration/    # データベースマイグレーション

apps/
├── command-api/            # コマンド側HTTP/gRPCサーバー
├── query-api/             # クエリ側GraphQLサーバー
└── read-model-updater/    # イベントプロセッサ（イベントから読み取りモデルを更新）
```

### 主要アーキテクチャパターン

1. **イベントソーシング**: Pekko Persistenceを使用して全ての状態変更をイベントとして記録
2. **CQRS**: 書き込み（コマンド）と読み取り（クエリ）のモデルを分離
3. **アクターモデル**: コマンド処理にPekko型付きアクターを使用
4. **イベント駆動**: イベント処理により読み取りモデルを非同期に更新
5. **Protobuf**: イベント/スナップショットのシリアライゼーションに使用
6. **GraphQL**: Sangria GraphQLでクエリAPIを公開

### 技術スタック

- **言語**: Scala 3
- **ビルドツール**: SBT
- **アクターフレームワーク**: Apache Pekko (型付きアクター、永続化、クラスター)
- **シリアライゼーション**: Protocol Buffers (ScalaPB経由)
- **クエリAPI**: GraphQL (Sangria)
- **データベースアクセス**: Slick
- **データベース**: PostgreSQL
- **HTTP**: Pekko HTTP
- **非同期/エフェクト**: ZIO
- **テスティング**: ScalaTest


## 実装上の重要事項

### Protobufファイル
- Protocol Buffer定義は `src/main/protobuf/` ディレクトリに配置
- `.proto` ファイル変更後は `sbt compile` でScalaコードを再生成
- 生成コードは `target/scala-*/pekko-grpc/main/` に出力される

### イベントシリアライゼーション
- イベントはProtocol Buffersでシリアライズ
- カスタムシリアライザは `SerializerWithStringManifest` を継承
- シリアライザIDは一意である必要がある（application.confで設定）

### データベーススキーマ
- クエリ側はPostgreSQLとFlywayマイグレーションを使用
- DAOクラスはsbt-dao-generatorでデータベーススキーマから自動生成
- スキーマ変更後は `sbt "queryInterfaceAdapter/generateAllWithDb"` でDAOを再生成

### テスティング
- ユニットテストはScalaTestを使用
- Pekkoアクターは `ActorTestKit` でテスト
- JVM状態の問題を避けるため、テストでは `fork := true` を使用

### Gitコミットガイドライン
- Conventional Commits形式を使用
- コミットメッセージは日本語で記述
- コミットに「Claude Code」への参照を含めない

## 開発ワークフロー

1. まずドメイン層またはユースケース層を変更
2. 新機能を公開するためインターフェースアダプターを更新
3. イベント変更時はprotobuf定義とシリアライザを更新
4. クエリ側変更時はマイグレーション更新後にDAOを再生成
5. `sbt compile` でビルドが通ることを確認
6. `sbt test` でテストが通ることを確認
7. コミット前に `sbt lint` でコードスタイルをチェック