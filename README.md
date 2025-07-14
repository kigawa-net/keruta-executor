# keruta-executor

Keruta Executorは、coderを使用してkeruta-apiからタスクを実行するKerutaタスク実行システムのコンポーネントです。

## 概要

Keruta Executorは定期的にkeruta-apiから保留中のタスクをポーリングし、coderを使用して実行し、その状態をkeruta-apiで更新します。タスクの実行はSSH経由で行うことができます。

## アーキテクチャ

Keruta Executorは以下のコンポーネントを持つSpring Bootアプリケーションとして構成されています：

- `TaskProcessor`：定期的にkeruta-apiから保留中のタスクをポーリングし、その実行をCoderExecutionServiceに委譲します
- `TaskApiService`：keruta-apiとやり取りしてタスクを取得し、その状態を更新し、ログを追加します
- `CoderExecutionService`：coderを使用してタスクを実行します
- `SshService`：SSH経由でコマンドを実行します

## 設定

Keruta Executorは`application.properties`で以下のプロパティを使用して設定できます：

```properties
# Keruta Executor設定
keruta.executor.api-base-url=http://keruta-api:8080
keruta.executor.processing-delay=10000

# Coder設定
keruta.executor.coder.command=bash
keruta.executor.coder.working-dir=/tmp/coder
keruta.executor.coder.timeout=1800000

# SSH設定
keruta.executor.ssh.host=localhost
keruta.executor.ssh.port=22
keruta.executor.ssh.username=root
# keruta.executor.ssh.password=
# keruta.executor.ssh.private-key-path=
# keruta.executor.ssh.private-key-passphrase=
keruta.executor.ssh.connection-timeout=30000
keruta.executor.ssh.strict-host-key-checking=false
```

### 設定プロパティ

- `keruta.executor.api-base-url`：keruta-apiのベースURL
- `keruta.executor.processing-delay`：タスク処理試行間の遅延（ミリ秒）
- `keruta.executor.coder.command`：coderを実行するコマンド
- `keruta.executor.coder.working-dir`：coderの作業ディレクトリ
- `keruta.executor.coder.timeout`：coder実行のタイムアウト（ミリ秒）

### SSH設定プロパティ

- `keruta.executor.ssh.host`：SSHサーバーのホスト名またはIPアドレス
- `keruta.executor.ssh.port`：SSHサーバーのポート
- `keruta.executor.ssh.username`：SSH認証のユーザー名
- `keruta.executor.ssh.password`：SSH認証のパスワード（オプション）
- `keruta.executor.ssh.private-key-path`：SSH認証の秘密鍵ファイルのパス（オプション）
- `keruta.executor.ssh.private-key-passphrase`：秘密鍵のパスフレーズ（オプション）
- `keruta.executor.ssh.connection-timeout`：SSH接続のタイムアウト（ミリ秒）
- `keruta.executor.ssh.strict-host-key-checking`：厳密なホストキーチェックを使用するかどうか

### 環境変数によるSSH自動設定

タスクスクリプトの環境変数を使用してSSH設定を自動的に構成することができます。以下の環境変数がサポートされています：

- `SSH_HOST`：SSHサーバーのホスト名またはIPアドレス
- `SSH_PORT`：SSHサーバーのポート
- `SSH_USERNAME`：SSH認証のユーザー名
- `SSH_PASSWORD`：SSH認証のパスワード
- `SSH_PRIVATE_KEY_PATH`：SSH認証の秘密鍵ファイルのパス
- `SSH_PRIVATE_KEY_PASSPHRASE`：秘密鍵のパスフレーズ
- `SSH_CONNECTION_TIMEOUT`：SSH接続のタイムアウト（ミリ秒）
- `SSH_STRICT_HOST_KEY_CHECKING`：厳密なホストキーチェックを使用するかどうか（true/false）

これらの環境変数は、keruta-apiのタスクスクリプトAPIを通じて提供されます。環境変数が設定されていない場合は、`application.properties`の設定が使用されます。

## ビルドと実行

### 方法1: Gradleで実行

#### ビルド

```bash
./gradlew build
```

#### 実行

```bash
./gradlew bootRun
```

### 方法2: Dockerで実行

```bash
# アプリケーションとMongoDBの起動
docker-compose up -d
```

アプリケーションは http://localhost:8081 で起動します。

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
