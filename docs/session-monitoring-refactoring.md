# SessionMonitoringServiceリファクタリングドキュメント

## 1. リファクタリング概要

### 背景
SessionMonitoringServiceは当初797行の巨大なサービスクラスでしたが、単一責任の原則に従い、より保守しやすい構造に分割されました。このリファクタリングにより、4つの専門化されたサービスに責任が分散され、全体で約254行にまで削減されました。

### リファクタリングの目的
- **単一責任の原則（SRP）の適用**: 各サービスが明確な責任を持つ
- **保守性の向上**: 機能ごとに分離されたコードベース
- **テスタビリティの改善**: 個別サービスの単体テスト容易化
- **拡張性の確保**: 新機能追加時の影響範囲最小化

## 2. 新アーキテクチャ

### 分離前（リファクタリング前）
```
SessionMonitoringService (797行)
├── セッション監視ロジック
├── API通信処理
├── サーキットブレーカー機能
├── ワークスペース作成処理
├── リトライ機能
└── 例外処理
```

### 分離後（リファクタリング後）
```
SessionMonitoringService (254行)
├── セッション監視の協調制御
└── スケジューリング管理

SessionApiClient (293行)
├── API通信処理
├── HTTPリクエスト管理
└── レスポンス処理

CircuitBreakerService (136行)
├── サーキットブレーカー機能
├── リトライ機能
└── 指数バックオフ

WorkspaceCreationHandler (131行)
├── ワークスペース作成ロジック
├── テンプレート選択
└── 名前生成
```

## 3. サービス別責任詳細

### 3.1 SessionMonitoringService
**責任**: セッション状態の監視とワークスペースライフサイクルの協調制御

**主要機能**:
- **新規セッション監視** (`monitorNewSessions()`): 30秒間隔でPENDINGセッションを監視
- **非アクティブセッション監視** (`monitorInactiveSessions()`): 90秒間隔でINACTIVEセッションの再活性化チェック
- **アクティブセッション監視** (`monitorActiveSessions()`): 60秒間隔でACTIVEセッションのワークスペース状態確認

**スケジューリング設計**:
```kotlin
@Scheduled(fixedDelay = 30000)  // 新規セッション - 高頻度
@Scheduled(fixedDelay = 60000)  // アクティブセッション - 中頻度
@Scheduled(fixedDelay = 90000)  // 非アクティブセッション - 低頻度
```

**状態遷移管理**:
```
PENDING → ACTIVE (ワークスペース作成後)
ACTIVE → INACTIVE (全ワークスペース停止時)
INACTIVE → ACTIVE (ワークスペース再開時)
```

### 3.2 SessionApiClient
**責任**: REST API通信の専門化されたクライアント

**主要機能**:
- **セッション取得API**: ステータス別セッション一覧取得
- **ワークスペース管理API**: ワークスペース作成・開始・状態取得
- **状態更新API**: セッション状態の更新処理
- **テンプレート管理API**: 利用可能テンプレートの取得

**エラーハンドリング**:
```kotlin
try {
    restTemplate.exchange(url, HttpMethod.PUT, entity, SessionDto::class.java)
} catch (e: HttpClientErrorException) {
    // 4xx エラーの詳細ログ
} catch (e: HttpServerErrorException) {
    // 5xx エラーの詳細ログ
} catch (e: ResourceAccessException) {
    // ネットワークエラーの処理
}
```

**リトライ統合**:
- `updateSessionStatusWithRetry()`: 3回リトライ
- `getWorkspacesBySessionIdWithRetry()`: 3回リトライ
- `createWorkspaceForSessionWithRetry()`: 2回リトライ

### 3.3 CircuitBreakerService
**責任**: 障害の伝播防止と復旧制御

**サーキットブレーカー状態**:
```kotlin
enum class State {
    CLOSED,     // 正常動作
    OPEN,       // 障害により回路遮断
    HALF_OPEN   // 復旧テスト中
}
```

**動作パラメータ**:
- 障害閾値: 5回連続失敗
- タイムアウト: 60秒（回路開放時間）
- バックオフ戦略: 指数バックオフ + ジッター

**指数バックオフ実装**:
```kotlin
private fun calculateBackoffDelay(attempt: Int): Long {
    val baseDelay = 1000L * (1L shl attempt) // 1s, 2s, 4s, 8s...
    val jitter = Random.nextLong(0, baseDelay / 2)
    return baseDelay + jitter
}
```

### 3.4 WorkspaceCreationHandler
**責任**: ワークスペース作成の専門処理

**テンプレート選択アルゴリズム**:
1. **セッションタグマッチング**: セッションタグとテンプレート名・説明の一致確認
2. **Keruta専用テンプレート**: `keruta`を含むテンプレートの優先選択
3. **フォールバック**: 利用可能な最初のテンプレートを使用

**ワークスペース名生成**:
```kotlin
private fun generateWorkspaceName(session: SessionDto): String {
    val sanitizedSessionName = session.name
        .replace("[^a-zA-Z0-9-_]".toRegex(), "-")
        .replace("-+".toRegex(), "-")
        .trim('-')
        .take(20)
    
    val timestamp = System.currentTimeMillis().toString().takeLast(6)
    return "session-${session.id.take(8)}-$sanitizedSessionName-$timestamp"
}
```

## 4. 状態同期メカニズム

### 4.1 セッション-ワークスペース状態同期

**同期フロー**:
```
1. SessionMonitoringService がセッション状態を検出
2. CoderWorkspaceService でワークスペース状態を確認
3. 状態不整合時に SessionApiClient で状態更新
4. CircuitBreakerService で障害監視
```

**状態マッピング**:
```kotlin
// ワークスペース → セッション状態マッピング
when (workspace.status.lowercase()) {
    "running", "starting" -> "ACTIVE"
    "stopped", "pending", "failed" -> "INACTIVE"
}
```

### 4.2 障害復旧メカニズム

**復旧戦略**:
1. **即座の復旧**: 軽微な障害時のリトライ
2. **サーキットブレーカー**: 重大な障害時の一時停止
3. **段階的復旧**: HALF_OPEN状態での慎重な復旧テスト

**復旧タイムライン**:
```
障害発生 → リトライ(3回) → サーキットブレーカー開放(60秒) → 復旧テスト → 正常動作再開
```

## 5. サービス間統合

### 5.1 依存関係図
```
SessionMonitoringService
├── SessionApiClient (API通信)
├── CircuitBreakerService (障害制御)
├── WorkspaceCreationHandler (ワークスペース作成)
└── CoderWorkspaceService (Coder連携)

SessionApiClient
└── CircuitBreakerService (リトライ機能)

WorkspaceCreationHandler
├── CoderWorkspaceService (Coder API)
└── CoderTemplateService (テンプレート管理)
```

### 5.2 データフロー

**新規セッション処理フロー**:
```
1. SessionMonitoringService.monitorNewSessions()
2. SessionApiClient.getPendingSessions()
3. CoderWorkspaceService.getWorkspacesBySessionId()
4. WorkspaceCreationHandler.createCoderWorkspaceForSession()
5. SessionApiClient.updateSessionStatusWithRetry()
6. CircuitBreakerService.recordSuccess/Failure()
```

**アクティブセッション監視フロー**:
```
1. SessionMonitoringService.monitorActiveSessions()
2. SessionApiClient.getActiveSessions()
3. CoderWorkspaceService.getWorkspacesBySessionId()
4. 状態判定 (running/stopped)
5. SessionApiClient.updateSessionStatusWithRetry() または
   CoderWorkspaceService.startWorkspace()
```

## 6. 設定とカスタマイゼーション

### 6.1 スケジューリング設定
```yaml
# application.yml での設定例
spring:
  task:
    scheduling:
      pool:
        size: 5
```

### 6.2 サーキットブレーカー設定
```kotlin
// CircuitBreakerService.kt
private val failureThreshold = 5    // 障害閾値
private val timeout = 60000L        // 60秒タイムアウト
```

### 6.3 API通信設定
```yaml
keruta:
  executor:
    api:
      base-url: "http://localhost:8080"
    coder:
      base-url: "https://coder.example.com"
      token: "${CODER_TOKEN}"
```

## 7. 運用とモニタリング

### 7.1 ログ出力レベル

**INFO レベル**:
- セッション処理開始・完了
- ワークスペース作成・開始
- 状態遷移

**DEBUG レベル**:
- スケジューリング開始
- テンプレート選択詳細
- ワークスペース状態確認

**ERROR レベル**:
- API通信エラー
- ワークスペース作成失敗
- サーキットブレーカー開放

### 7.2 メトリクス監視推奨項目

**処理性能**:
- セッション処理時間
- API応答時間
- ワークスペース作成時間

**障害監視**:
- サーキットブレーカー開放回数
- API通信失敗率
- ワークスペース作成失敗率

**リソース使用**:
- スレッドプール使用率
- メモリ使用量
- ネットワーク接続数

## 8. 拡張とメンテナンス

### 8.1 新機能追加時の考慮事項

**新しい監視ロジック追加**:
- `SessionMonitoringService`に新しい`@Scheduled`メソッドを追加
- 必要に応じて専門サービスに委譲

**新しいAPI統合**:
- `SessionApiClient`に新しいメソッドを追加
- サーキットブレーカー統合を考慮

**新しい障害パターン**:
- `CircuitBreakerService`の閾値・タイムアウト調整
- カスタム復旧戦略の実装

### 8.2 テスト戦略

**単体テスト**:
- 各サービスの独立したテスト
- モックを使用した依存関係の分離

**統合テスト**:
- API通信の実際のテスト
- サーキットブレーカーの動作確認

**パフォーマンステスト**:
- 大量セッションでの処理性能
- 同時実行時の競合状態確認

## 9. トラブルシューティング

### 9.1 よくある問題と解決策

**セッション状態更新失敗**:
```bash
# ログ確認
grep "Failed to update session status" logs/keruta-executor.log

# サーキットブレーカー状態確認
grep "Circuit breaker is open" logs/keruta-executor.log
```

**ワークスペース作成失敗**:
```bash
# テンプレート可用性確認
curl ${KERUTA_API_URL}/api/v1/workspaces/templates

# Coder接続確認
curl -H "Authorization: Bearer ${CODER_TOKEN}" ${CODER_URL}/api/v2/templates
```

**API通信エラー**:
```bash
# 接続確認
curl ${KERUTA_API_URL}/api/v1/health

# 認証確認
curl -H "Authorization: Bearer ${API_TOKEN}" ${KERUTA_API_URL}/api/v1/sessions
```

### 9.2 パフォーマンス最適化

**スケジューリング間隔調整**:
- 負荷に応じてfixedDelay値を調整
- 処理時間がスケジューリング間隔を超える場合の対応

**API通信最適化**:
- コネクションプール設定
- タイムアウト値の最適化

**メモリ使用量最適化**:
- 大きなレスポンスのストリーミング処理
- 不要なオブジェクトの早期解放

## 10. 今後の改善予定

### 10.1 短期的改善
- メトリクス収集機能の追加
- より詳細なエラー分類
- 設定の外部化

### 10.2 中長期的改善
- 非同期処理の導入
- 分散処理対応
- AI/MLベースの予測的監視

---

**作成日**: 2025-01-XX  
**バージョン**: 1.0  
**メンテナー**: Keruta開発チーム