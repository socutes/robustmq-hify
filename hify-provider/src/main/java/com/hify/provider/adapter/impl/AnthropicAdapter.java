package com.hify.provider.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hify.common.http.LlmApiException;
import com.hify.common.http.LlmHttpClient;
import com.hify.provider.adapter.ProviderAdapter;
import com.hify.provider.dto.ChatMessage;
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
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnthropicAdapter implements ProviderAdapter {

    private final LlmHttpClient llmHttpClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> supportedTypes() {
        return List.of("ANTHROPIC");
    }

    // ── 连通性测试（不变）────────────────────────────────────────

    @Override
    public ConnectionTestResult testConnection(Provider provider, OkHttpClient testClient) {
        long start = System.currentTimeMillis();
        try {
            String body = llmHttpClient.get(modelsUrl(provider), authHeaders(provider), testClient);
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
        try {
            String body = llmHttpClient.get(modelsUrl(provider), authHeaders(provider), client);
            List<String> ids = new ArrayList<>();
            JsonNode data = objectMapper.readTree(body).path("data");
            if (data.isArray()) {
                data.forEach(n -> {
                    JsonNode id = n.path("id");
                    if (!id.isMissingNode()) ids.add(id.asText());
                });
            }
            return ids;
        } catch (Exception e) {
            log.warn("listModels failed provider={}: {}", provider.getName(), e.getMessage());
            return List.of();
        }
    }

    // ── 同步对话 ───────────────────────────────────────────────

    @Override
    public ChatResponse chat(Provider provider, ChatRequest request) {
        try {
            String body = llmHttpClient.post(
                    chatUrl(provider),
                    authHeaders(provider),
                    buildRequestBody(request, false));
            return parseResponse(body);
        } catch (LlmApiException e) {
            log.warn("chat failed provider={}: {}", provider.getName(), e.getMessage());
            return ChatResponse.error(e.getMessage());
        }
    }

    // ── 流式对话 ───────────────────────────────────────────────

    /**
     * Anthropic 流式格式（Server-Sent Events）：
     *   event: content_block_delta
     *   data:  {"type":"content_block_delta","delta":{"type":"text_delta","text":"..."}}
     *
     *   event: message_stop
     *   data:  {"type":"message_stop"}
     */
    @Override
    public ChatResponse streamChat(Provider provider, ChatRequest request, Consumer<String> onChunk) {
        StringBuilder content = new StringBuilder();
        String[] finishReason = {"stop"};

        llmHttpClient.stream(
                chatUrl(provider),
                authHeaders(provider),
                buildRequestBody(request, true),
                line -> {
                    if (line == null || !line.startsWith("data: ")) return;
                    String data = line.substring(6).trim();
                    String delta = parseDelta(data);
                    if (delta != null && !delta.isEmpty()) {
                        content.append(delta);
                        onChunk.accept(delta);
                    }
                    // message_delta 事件含 stop_reason
                    String reason = parseStopReason(data);
                    if (reason != null) finishReason[0] = reason;
                });

        return ChatResponse.of(content.toString(), finishReason[0], null);
    }

    // ── 私有工具方法 ──────────────────────────────────────────

    private String chatUrl(Provider provider) {
        return provider.getBaseUrl().stripTrailing() + "/v1/messages";
    }

    private String modelsUrl(Provider provider) {
        return provider.getBaseUrl().stripTrailing() + "/v1/models";
    }

    private Map<String, String> authHeaders(Provider provider) {
        Map<String, Object> auth = provider.getAuthConfig();
        String apiKey = getAuth(auth, "apiKey");
        String version = auth.containsKey("anthropicVersion")
                ? auth.get("anthropicVersion").toString()
                : "2023-06-01";
        return Map.of(
                "x-api-key", apiKey,
                "anthropic-version", version
        );
    }

    /**
     * Anthropic messages API 的请求体结构与 OpenAI 有两处不同：
     * 1. system prompt 是顶层字段，不在 messages 数组里
     * 2. max_tokens 是必填字段
     */
    private String buildRequestBody(ChatRequest request, boolean stream) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", request.getModelId());
            root.put("stream", stream);
            root.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 2048);
            if (request.getTemperature() != null) {
                root.put("temperature", request.getTemperature().doubleValue());
            }

            // 把 system 消息提取出来单独放顶层
            ArrayNode msgs = root.putArray("messages");
            for (ChatMessage m : request.getMessages()) {
                if ("system".equals(m.getRole())) {
                    root.put("system", m.getContent());
                    continue;
                }
                ObjectNode node = msgs.addObject();
                node.put("role", m.getRole());
                node.put("content", m.getContent());
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("构造请求体失败", e);
        }
    }

    private ChatResponse parseResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String content = root.path("content").path(0).path("text").asText("");
            String stopReason = root.path("stop_reason").asText("stop");
            JsonNode usage = root.path("usage");
            Integer tokens = usage.path("output_tokens").canConvertToInt()
                    ? usage.path("output_tokens").intValue() : null;
            return ChatResponse.of(content, stopReason, tokens);
        } catch (Exception e) {
            log.warn("parseResponse failed: {}", e.getMessage());
            return ChatResponse.error("响应解析失败");
        }
    }

    private String parseDelta(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            // content_block_delta 事件
            if ("content_block_delta".equals(root.path("type").asText())) {
                return root.path("delta").path("text").asText(null);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String parseStopReason(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            if ("message_delta".equals(root.path("type").asText())) {
                return root.path("delta").path("stop_reason").asText(null);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private int parseDataArraySize(String body) {
        try {
            JsonNode data = objectMapper.readTree(body).path("data");
            return data.isArray() ? data.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String getAuth(Map<String, Object> auth, String key) {
        if (auth == null || !auth.containsKey(key) || auth.get(key) == null) {
            throw new IllegalArgumentException("authConfig 缺少字段：" + key);
        }
        return auth.get(key).toString();
    }
}
