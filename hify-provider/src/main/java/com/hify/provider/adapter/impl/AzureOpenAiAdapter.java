package com.hify.provider.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import com.hify.provider.dto.ConnectionTestResult;
import com.hify.provider.entity.Provider;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Azure OpenAI Adapter。
 * chat / streamChat 与 OpenAI 格式完全一致，直接复用父类。
 * 仅覆盖 URL 构造（deployment 路径 + api-version）和认证 Header。
 */
@Slf4j
@Component
@Profile("!mock")
public class AzureOpenAiAdapter extends OpenAiAdapter {

    public AzureOpenAiAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        super(llmHttpClient, objectMapper);
    }

    @Override
    public List<String> supportedTypes() {
        return List.of("AZURE_OPENAI");
    }

    @Override
    public ConnectionTestResult testConnection(Provider provider, OkHttpClient testClient) {
        long start = System.currentTimeMillis();
        try {
            String body = llmHttpClient.get(modelsUrl(provider), authHeaders(provider), testClient);
            int latency = (int) (System.currentTimeMillis() - start);
            // Azure 模型列表在 value 字段，不是 data
            JsonNode value = objectMapper.readTree(body).path("value");
            int count = value.isArray() ? value.size() : 0;
            return ConnectionTestResult.ok(latency, count);
        } catch (Exception e) {
            return ConnectionTestResult.fail("测试异常：" + e.getMessage());
        }
    }

    @Override
    public List<String> listModels(Provider provider, OkHttpClient client) {
        try {
            String body = llmHttpClient.get(modelsUrl(provider), authHeaders(provider), client);
            List<String> ids = new ArrayList<>();
            JsonNode value = objectMapper.readTree(body).path("value");
            if (value.isArray()) {
                value.forEach(n -> {
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

    /** Azure chat URL: {baseUrl}/openai/deployments/{deploymentName}/chat/completions?api-version=xxx */
    @Override
    protected String chatUrl(Provider provider) {
        String deployment = getAuth(provider, "deploymentName");
        String apiVersion = getAuth(provider, "apiVersion");
        return provider.getBaseUrl().stripTrailing()
                + "/openai/deployments/" + deployment
                + "/chat/completions?api-version=" + apiVersion;
    }

    @Override
    protected String modelsUrl(Provider provider) {
        String apiVersion = getAuth(provider, "apiVersion");
        return provider.getBaseUrl().stripTrailing() + "/openai/models?api-version=" + apiVersion;
    }

    @Override
    protected Map<String, String> authHeaders(Provider provider) {
        return Map.of("api-key", getAuth(provider, "apiKey"));
    }
}
