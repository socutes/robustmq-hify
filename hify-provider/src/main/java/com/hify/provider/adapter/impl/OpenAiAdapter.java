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
public class OpenAiAdapter implements ProviderAdapter {

    protected final LlmHttpClient llmHttpClient;
    protected final ObjectMapper objectMapper;

    @Override
    public List<String> supportedTypes() {
        return List.of("OPENAI");
    }

    // ── 连通性测试 ─────────────────────────────────────────────

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
            return parseDataIds(body);
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
                    if ("[DONE]".equals(data)) return;
                    String delta = parseDelta(data);
                    if (delta != null && !delta.isEmpty()) {
                        content.append(delta);
                        onChunk.accept(delta);
                    }
                    String reason = parseFinishReason(data);
                    if (reason != null) finishReason[0] = reason;
                });

        return ChatResponse.of(content.toString(), finishReason[0], null);
    }

    // ── 供子类复用的工具方法 ────────────────────────────────────

    protected String chatUrl(Provider provider) {
        return provider.getBaseUrl().stripTrailing() + "/v1/chat/completions";
    }

    protected String modelsUrl(Provider provider) {
        return provider.getBaseUrl().stripTrailing() + "/v1/models";
    }

    protected Map<String, String> authHeaders(Provider provider) {
        return Map.of("Authorization", "Bearer " + getAuth(provider, "apiKey"));
    }

    protected String buildRequestBody(ChatRequest request, boolean stream) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", request.getModelId());
            root.put("stream", stream);
            if (request.getTemperature() != null) {
                root.put("temperature", request.getTemperature().doubleValue());
            }
            if (request.getMaxTokens() != null) {
                root.put("max_tokens", request.getMaxTokens());
            }
            ArrayNode msgs = root.putArray("messages");
            for (ChatMessage m : request.getMessages()) {
                ObjectNode node = msgs.addObject();
                node.put("role", m.getRole());
                node.put("content", m.getContent());
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("构造请求体失败", e);
        }
    }

    protected ChatResponse parseResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choice = root.path("choices").path(0);
            String content = choice.path("message").path("content").asText("");
            String finishReason = choice.path("finish_reason").asText("stop");
            Integer tokens = root.path("usage").path("completion_tokens").canConvertToInt()
                    ? root.path("usage").path("completion_tokens").intValue() : null;
            return ChatResponse.of(content, finishReason, tokens);
        } catch (Exception e) {
            log.warn("parseResponse failed: {}", e.getMessage());
            return ChatResponse.error("响应解析失败");
        }
    }

    protected String parseDelta(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            return root.path("choices").path(0).path("delta").path("content").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    protected String parseFinishReason(String data) {
        try {
            JsonNode reason = objectMapper.readTree(data)
                    .path("choices").path(0).path("finish_reason");
            return reason.isNull() || reason.isMissingNode() ? null : reason.asText();
        } catch (Exception e) {
            return null;
        }
    }

    protected String getAuth(Provider provider, String key) {
        Map<String, Object> auth = provider.getAuthConfig();
        if (auth == null || !auth.containsKey(key) || auth.get(key) == null) {
            throw new IllegalArgumentException("authConfig 缺少字段：" + key);
        }
        return auth.get(key).toString();
    }

    protected int parseDataArraySize(String body) {
        try {
            JsonNode data = objectMapper.readTree(body).path("data");
            return data.isArray() ? data.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    protected List<String> parseDataIds(String body) {
        List<String> ids = new ArrayList<>();
        try {
            JsonNode data = objectMapper.readTree(body).path("data");
            if (data.isArray()) {
                data.forEach(n -> {
                    JsonNode id = n.path("id");
                    if (!id.isMissingNode()) ids.add(id.asText());
                });
            }
        } catch (Exception e) {
            log.warn("parseDataIds failed: {}", e.getMessage());
        }
        return ids;
    }
}
