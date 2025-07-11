# keruta-executor

Keruta Executorは、coderを使用してkeruta-apiからタスクを実行するKerutaタスク実行システムのコンポーネントです。

## 概要

Keruta Executorは定期的にkeruta-apiから保留中のタスクをポーリングし、coderを使用して実行し、その状態をkeruta-apiで更新します。

## アーキテクチャ

Keruta Executorは以下のコンポーネントを持つSpring Bootアプリケーションとして構成されています：

- `TaskProcessor`：定期的にkeruta-apiから保留中のタスクをポーリングし、その実行をCoderExecutionServiceに委譲します
- `TaskApiService`：keruta-apiとやり取りしてタスクを取得し、その状態を更新し、ログを追加します
- `CoderExecutionService`：coderを使用してタスクを実行します

## 設定

Keruta Executorは`application.properties`で以下のプロパティを使用して設定できます：

```properties
# Keruta Executor設定
keruta.executor.api-base-url=http://localhost:8080
keruta.executor.processing-delay=10000

# Coder設定
keruta.executor.coder.command=coder
keruta.executor.coder.working-dir=/tmp/coder
keruta.executor.coder.timeout=1800000
```

### 設定プロパティ

- `keruta.executor.api-base-url`：keruta-apiのベースURL
- `keruta.executor.processing-delay`：タスク処理試行間の遅延（ミリ秒）
- `keruta.executor.coder.command`：coderを実行するコマンド
- `keruta.executor.coder.working-dir`：coderの作業ディレクトリ
- `keruta.executor.coder.timeout`：coder実行のタイムアウト（ミリ秒）

## ビルドと実行

### ビルド

```bash
./gradlew build
```

### 実行

```bash
./gradlew bootRun
```

## 開発

### テスト

```bash
./gradlew test
```

### コード品質

```bash
./gradlew ktlintCheck
./gradlew ktlintFormat
```
