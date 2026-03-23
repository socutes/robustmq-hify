package com.hify.provider.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmApiException;
import com.hify.common.http.LlmHttpClient;
import com.hify.provider.adapter.ProviderAdapter;
import com.hify.provider.dto.ChatRequest;
import com.hify.provider.dto.ChatResponse;
import com.hify.provider.dto.ConnectionTestResult;
import com.hify.provider.entity.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaAdapter implements ProviderAdapter {

    private final LlmHttpClient llmHttpClient;
    private final ObjectMapper objectMapper;
    // 复用 OpenAI 格式的请求体构造和响应解析
    private final OpenAiAdapter openAiAdapter;

    @Override
    public List<String> supportedTypes() {
        return List.of("OLLAMA");
    }

    // ── 连通性测试（不变）────────────────────────────────────────

    @Override
    public ConnectionTestResult testConnection(Provider provider, OkHttpClient testClient) {
        long start = System.currentTimeMillis();
        try {
            String body = llmHttpClient.get(tagsUrl(provider), null, testClient);
            int latency = (int) (System.currentTimeMillis() - start);
            return ConnectionTestResult.ok(latency, parseModelsArraySize(body));
        } catch (LlmApiException e) {
            return ConnectionTestResult.fail(e.getMessage());
        } catch (Exception e) {
            return ConnectionTestResult.fail("测试异常：" + e.getMessage());
        }
    }

    @Override
    public List<String> listModels(Provider provider, OkHttpClient client) {
        try {
            String body = llmHttpClient.get(tagsUrl(provider), null, client);
            List<String> names = new ArrayList<>();
            JsonNode models = objectMapper.readTree(body).path("models");
            if (models.isArray()) {
                models.forEach(n -> {
                    JsonNode name = n.path("name");
                    if (!name.isMissingNode()) names.add(name.asText());
                });
            }
            return names;
        } catch (Exception e) {
            log.warn("listModels failed provider={}: {}", provider.getName(), e.getMessage());
            return List.of();
        }
    }

    // ── 同步对话（Ollama /v1/chat/completions 兼容 OpenAI 格式）─

    @Override
    public ChatResponse chat(Provider provider, ChatRequest request) {
        try {
            // Ollama 兼容 OpenAI 的 /v1/chat/completions，无需认证
            String body = llmHttpClient.post(
                    chatUrl(provider),
                    null,
                    openAiAdapter.buildRequestBody(request, false));
            return openAiAdapter.parseResponse(body);
        } catch (LlmApiException e) {
            log.warn("chat failed provider={}: {}", provider.getName(), e.getMessage());
            return ChatResponse.error(e.getMessage());
        }
    }

    // ── 流式对话 ───────────────────────────────────────────────

    /**
     * Ollama 流式格式（每行是完整 JSON，无 "data: " 前缀）：
     *   {"model":"llama3","message":{"role":"assistant","content":"你"},"done":false}
     *   {"model":"llama3","message":{"role":"assistant","content":"好"},"done":false}
     *   {"model":"llama3","done":true,"done_reason":"stop"}
     */
    @Override
    public ChatResponse streamChat(Provider provider, ChatRequest request, Consumer<String> onChunk) {
        StringBuilder content = new StringBuilder();
        String[] finishReason = {"stop"};

        llmHttpClient.stream(
                chatUrl(provider),
                null,
                openAiAdapter.buildRequestBody(request, true),
                line -> {
                    if (line == null) return;
                    try {
                        JsonNode root = objectMapper.readTree(line);
                        String delta = root.path("message").path("content").asText(null);
                        if (delta != null && !delta.isEmpty()) {
                            content.append(delta);
                            onChunk.accept(delta);
                        }
                        if (root.path("done").asBoolean(false)) {
                            finishReason[0] = root.path("done_reason").asText("stop");
                        }
                    } catch (Exception e) {
                        log.warn("parseDelta failed: {}", e.getMessage());
                    }
                });

        return ChatResponse.of(content.toString(), finishReason[0], null);
    }

    private String chatUrl(Provider provider) {
        return provider.getBaseUrl().stripTrailing() + "/v1/chat/completions";
    }

    private String tagsUrl(Provider provider) {
        return provider.getBaseUrl().stripTrailing() + "/api/tags";
    }

    private int parseModelsArraySize(String body) {
        try {
            JsonNode models = objectMapper.readTree(body).path("models");
            return models.isArray() ? models.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
