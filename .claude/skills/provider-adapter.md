# Skill: 新增 Provider Adapter

触发方式：当用户说"接入新供应商"、"新增 XX 提供商支持"、"加一个 Adapter" 时按此流程推进。

---

## 背景

Provider 的连通性测试、模型同步、调用逻辑按供应商类型有差异。
已重构为策略模式（switch-case 已删除）：

```
ProviderAdapterFactory
  └── Map<String, ProviderAdapter>   ← Spring 启动时自动扫描注册
        ├── OpenAiAdapter            (OPENAI)
        ├── OpenAiCompatibleAdapter  (OPENAI_COMPATIBLE, DEEPSEEK) ← 继承 OpenAiAdapter，零额外代码
        ├── AnthropicAdapter         (ANTHROPIC)
        ├── AzureOpenAiAdapter       (AZURE_OPENAI)
        └── OllamaAdapter            (OLLAMA)
```

**加新供应商只需两步：实现 Adapter + 加 `@Component`，不改任何已有代码。**

---

## Step 1 — 分析目标供应商 API

**目标**：搞清楚接入该供应商需要哪些差异化实现。

| 问题 | 说明 |
|------|------|
| 认证方式 | Bearer Token / API Key Header / 双 Header / 无认证？ |
| 列模型接口 | URL 路径？返回结构（`data[].id` / `models[].name` / 其他）？ |
| 必填 authConfig 字段 | 如 `apiKey`、`apiVersion`、`anthropicVersion` |
| baseUrl 默认值 | 官方默认是什么？用户可否自定义？ |
| 特殊请求头 | 如 Anthropic 的 `anthropic-version` |

**产出物**：一份简短的 API 特征说明（口头或注释均可）

> ⚠️ **等待用户确认**：API 特征分析结果确认后再写代码

---

## Step 2 — 实现 Adapter

**目标**：新建一个实现 `ProviderAdapter` 接口的类，加 `@Component` 即自动注册。

**接口定义**（`hify-provider/.../adapter/ProviderAdapter.java`）：

```java
public interface ProviderAdapter {
    /** 该 Adapter 支持的供应商类型（大写），可多个 */
    List<String> supportedTypes();

    /** 连通性测试，返回延迟和模型数 */
    ConnectionTestResult testConnection(Provider provider, OkHttpClient testClient);

    /** 拉取模型列表，返回模型 ID/名称列表 */
    List<String> listModels(Provider provider, OkHttpClient client);
}
```

**文件位置**：`hify-provider/src/main/java/com/hify/provider/adapter/impl/XxxAdapter.java`

**实现模板**（以 OpenAI 风格为例）：

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class XxxAdapter implements ProviderAdapter {

    private final LlmHttpClient llmHttpClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> supportedTypes() {
        return List.of("XXX");
    }

    @Override
    public ConnectionTestResult testConnection(Provider provider, OkHttpClient testClient) {
        long start = System.currentTimeMillis();
        try {
            String url = provider.getBaseUrl().stripTrailing() + "/v1/models";
            Map<String, String> headers = Map.of("Authorization", "Bearer " + getAuth(provider, "apiKey"));

            String body = llmHttpClient.get(url, headers, testClient);
            int latency = (int) (System.currentTimeMillis() - start);
            return ConnectionTestResult.ok(latency, parseDataArraySize(body));
        } catch (LlmApiException e) {
            return ConnectionTestResult.fail(e.getMessage());
        } catch (Exception e) {
            return ConnectionTestResult.fail("测试异常：" + e.getMessage());
        }
    }

    @Override
    public List<String> listModels(Provider provider, OkHttpClient client) {
        // 参考 testConnection 逻辑，解析并返回模型 ID 列表
    }

    private String getAuth(Provider provider, String key) {
        Map<String, Object> auth = provider.getAuthConfig();
        if (auth == null || !auth.containsKey(key) || auth.get(key) == null) {
            throw new IllegalArgumentException("authConfig 缺少字段：" + key);
        }
        return auth.get(key).toString();
    }
}
```

**如果与 OpenAI 完全兼容**，直接继承 `OpenAiAdapter`，只覆盖 `supportedTypes()`：

```java
@Component
public class XxxAdapter extends OpenAiAdapter {
    public XxxAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        super(llmHttpClient, objectMapper);
    }

    @Override
    public List<String> supportedTypes() {
        return List.of("XXX");
    }
}
```

**验证**：
```bash
mvn clean install -DskipTests -pl hify-provider -am
# 启动后日志确认：ProviderAdapterFactory registered types: [..., XXX]
```

---

## Step 3 — 更新前端类型下拉（如需要）

前端 `ProviderList.vue` 的 `providerTypes` 数组加一项：

```ts
{ label: 'XXX 显示名', value: 'XXX' }
```

数据库 `provider.type` 是 varchar，无需迁移。

---

## Step 4 — 验证

```bash
# 创建新供应商
curl -s -X POST http://localhost:8080/api/v1/providers \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "测试-XXX",
    "type": "XXX",
    "baseUrl": "https://api.xxx.com",
    "authConfig": { "apiKey": "sk-test-xxx" }
  }' | jq .

# 连通性测试
curl -s -X POST http://localhost:8080/api/v1/providers/{id}/test-connection | jq .
```

**预期**：
- 真实 key：`success: true`，有 `latencyMs` 和 `modelCount`
- 假 key：`success: false`，`errorMessage` 有明确提示，**不能是 500**

---

## 常见坑

| 现象 | 原因 | 修复 |
|------|------|------|
| Factory 找不到新 Adapter | 忘加 `@Component` | 加上即可，Factory 自动扫描 |
| `supportedTypes()` 不匹配 | 大小写不一致 | Factory 和 Adapter 都统一用大写 |
| authConfig 字段缺失导致 500 | 前端创建时漏传必填字段 | `getAuth()` 异常信息要说明缺哪个字段 |
| 连通性测试超时 | 没用 `testClient`，用了无超时的默认客户端 | 必须用传入的 `testClient`（10s 超时） |
| 模型数量永远是 0 | 响应结构字段名不是 `data`（如 Ollama 用 `models`） | `objectMapper.readTree(body)` 打印原始结构确认字段名 |
