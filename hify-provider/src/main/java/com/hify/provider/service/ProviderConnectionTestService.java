package com.hify.provider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.LlmApiException;
import com.hify.common.http.LlmHttpClient;
import com.hify.provider.dto.ConnectionTestResult;
import com.hify.provider.entity.Provider;
import com.hify.provider.mapper.ProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderConnectionTestService {

    private final LlmHttpClient llmHttpClient;
    private final ObjectMapper objectMapper;
    private final ProviderMapper providerMapper;

    /** 连通性测试专用 10s 超时客户端 */
    private final OkHttpClient testClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public ConnectionTestResult testById(Long id) {
        Provider provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BizException(ErrorCode.PROVIDER_NOT_FOUND);
        }
        return test(provider);
    }

    public ConnectionTestResult test(Provider provider) {
        long start = System.currentTimeMillis();
        try {
            return switch (provider.getType().toUpperCase()) {
                case "OPENAI", "OPENAI_COMPATIBLE", "DEEPSEEK" -> testOpenAiCompatible(provider, start);
                case "AZURE_OPENAI"                            -> testAzure(provider, start);
                case "ANTHROPIC"                               -> testAnthropic(provider, start);
                case "OLLAMA"                                  -> testOllama(provider, start);
                default -> ConnectionTestResult.fail("不支持的供应商类型：" + provider.getType());
            };
        } catch (LlmApiException e) {
            log.warn("连通性测试失败 provider={} type={}: {}", provider.getName(), e.getType(), e.getMessage());
            return ConnectionTestResult.fail(e.getMessage());
        } catch (Exception e) {
            log.warn("连通性测试异常 provider={}: {}", provider.getName(), e.getMessage());
            return ConnectionTestResult.fail("测试异常：" + e.getMessage());
        }
    }

    // ── 各供应商测试实现 ───────────────────────────────────────

    /** OpenAI / OpenAI-compatible / DeepSeek：GET /v1/models，Bearer Token 认证 */
    private ConnectionTestResult testOpenAiCompatible(Provider provider, long start) throws Exception {
        String apiKey = getAuthValue(provider, "apiKey");
        String url = provider.getBaseUrl().stripTrailing() + "/v1/models";

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);

        String body = llmHttpClient.get(url, headers, testClient);
        int latency = (int) (System.currentTimeMillis() - start);
        int modelCount = parseDataArraySize(body);
        return ConnectionTestResult.ok(latency, modelCount);
    }

    /** Azure OpenAI：部署级 URL，api-key Header 认证 */
    private ConnectionTestResult testAzure(Provider provider, long start) throws Exception {
        String apiKey    = getAuthValue(provider, "apiKey");
        String apiVersion = getAuthValue(provider, "apiVersion");
        // Azure 调用 /openai/models?api-version=xxx 验证连通性
        String url = provider.getBaseUrl().stripTrailing()
                + "/openai/models?api-version=" + apiVersion;

        Map<String, String> headers = new HashMap<>();
        headers.put("api-key", apiKey);

        String body = llmHttpClient.get(url, headers, testClient);
        int latency = (int) (System.currentTimeMillis() - start);
        int modelCount = parseDataArraySize(body);
        return ConnectionTestResult.ok(latency, modelCount);
    }

    /** Anthropic：GET /v1/models，x-api-key + anthropic-version Header */
    private ConnectionTestResult testAnthropic(Provider provider, long start) throws Exception {
        String apiKey  = getAuthValue(provider, "apiKey");
        String version = getAuthValue(provider, "anthropicVersion");
        String url = provider.getBaseUrl().stripTrailing() + "/v1/models";

        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", version);

        String body = llmHttpClient.get(url, headers, testClient);
        int latency = (int) (System.currentTimeMillis() - start);
        // Anthropic 返回 {"data": [...]}
        int modelCount = parseDataArraySize(body);
        return ConnectionTestResult.ok(latency, modelCount);
    }

    /** Ollama：GET /api/tags，无认证，返回 {"models": [...]} */
    private ConnectionTestResult testOllama(Provider provider, long start) throws Exception {
        String url = provider.getBaseUrl().stripTrailing() + "/api/tags";

        String body = llmHttpClient.get(url, null, testClient);
        int latency = (int) (System.currentTimeMillis() - start);
        int modelCount = 0;
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode models = root.path("models");
            if (models.isArray()) modelCount = models.size();
        } catch (Exception ignored) {}
        return ConnectionTestResult.ok(latency, modelCount);
    }

    // ── 工具方法 ──────────────────────────────────────────────

    /** 从 authConfig 取值，缺失时抛出明确提示 */
    private String getAuthValue(Provider provider, String key) {
        Map<String, Object> auth = provider.getAuthConfig();
        if (auth == null || !auth.containsKey(key) || auth.get(key) == null) {
            throw new IllegalArgumentException("authConfig 缺少字段：" + key);
        }
        return auth.get(key).toString();
    }

    /** 解析 OpenAI 风格的 {"data": [...]} 返回，取数组长度 */
    private int parseDataArraySize(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            return data.isArray() ? data.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
