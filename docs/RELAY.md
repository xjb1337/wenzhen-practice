# 内置服务中转

Android 端支持两种 AI 服务来源：

- 「自己的 Key」：直接请求用户配置的 DeepSeek Base URL。
- 「内置服务」：用户兑换一次性邀请码后，请求发往内置 Cloudflare Worker 中转。

APK 不包含 DeepSeek API Key。内置服务地址常量位于
`app/src/main/java/com/xjb/wenzhen/data/settings/AppSettings.kt`：

```kotlin
const val RELAY_BASE_URL = "https://wenzhen-relay.wenzhen-xjb1337.workers.dev"
```

若 Worker 域名变更，只改这一处（之后也可迁到 `BuildConfig`）。Android 端只接受 HTTPS 地址。

## HTTP 契约

所有路径均以 relay Base URL 为根（客户端会补上 `/v1`）。

### 兑换邀请码

`POST /v1/redeem`

```json
{"code":"WZ-...","deviceId":"..."}
```

成功：

```json
{"sessionToken":"...","expiresAt":123}
```

失败：

```json
{"error":{"code":"invite_used|invite_invalid|relay_disabled","message":"中文"}}
```

### 对话补全

`POST /v1/chat/completions`

请求体与 OpenAI/DeepSeek Chat Completions 兼容。请求头：

```text
Authorization: Bearer <sessionToken>
X-Device-Id: <deviceId>
```

失败：

```json
{"error":{"code":"quota_device|quota_spend|relay_disabled","message":"中文"}}
```

### 健康检查

`GET /v1/health`，返回 200 表示可用。若接口缺失，Android 端会回退到
`max_tokens=1` 的最小补全请求。

客户端会把额度错误映射为「今日额度已用完」，把邀请码错误映射为
「邀请码无效或已使用」；服务端返回中文说明时优先展示服务端文案。
