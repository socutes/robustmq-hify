package com.hify.provider.adapter.impl;

import com.hify.provider.adapter.ProviderAdapter;
import com.hify.provider.dto.ChatRequest;
import com.hify.provider.dto.ChatResponse;
import com.hify.provider.dto.ConnectionTestResult;
import com.hify.provider.entity.Provider;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * mock profile 下替换所有真实 LLM 调用，直接返回假数据。
 * 仅用于本地开发验证接口，不发送任何 HTTP 请求。
 */
@Component
@Profile("mock")
public class MockProviderAdapter implements ProviderAdapter {

    private static final String MOCK_REPLY = "这是 Mock 回复。真实环境会调用 LLM 返回流式内容。";

    @Override
    public List<String> supportedTypes() {
        // 注册所有类型，让 Factory 在 mock profile 下统一路由到这里
        return List.of("OPENAI", "OPENAI_COMPATIBLE", "ANTHROPIC", "AZURE_OPENAI", "OLLAMA", "DEEPSEEK");
    }

    @Override
    public ConnectionTestResult testConnection(Provider provider, OkHttpClient testClient) {
        return ConnectionTestResult.ok(42, 10);
    }

    @Override
    public List<String> listModels(Provider provider, OkHttpClient client) {
        return List.of("mock-model-1", "mock-model-2");
    }

    @Override
    public ChatResponse chat(Provider provider, ChatRequest request) {
        return ChatResponse.of(MOCK_REPLY, "stop", 20);
    }

    @Override
    public ChatResponse streamChat(Provider provider, ChatRequest request, Consumer<String> onChunk) {
        // 模拟逐字流式输出
        for (String word : MOCK_REPLY.split("")) {
            onChunk.accept(word);
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        return ChatResponse.of(MOCK_REPLY, "stop", 20);
    }
}
