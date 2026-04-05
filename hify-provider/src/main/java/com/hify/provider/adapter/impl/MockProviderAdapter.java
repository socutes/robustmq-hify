package com.hify.provider.adapter.impl;

import com.hify.provider.adapter.ProviderAdapter;
import com.hify.provider.dto.ChatMessage;
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
 * 能感知 system prompt 里的 RAG 参考资料，模拟引用文档回答。
 */
@Component
@Profile("mock")
public class MockProviderAdapter implements ProviderAdapter {

    @Override
    public List<String> supportedTypes() {
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
        String reply = buildReply(request);
        return ChatResponse.of(reply, "stop", reply.length() / 2);
    }

    @Override
    public ChatResponse streamChat(Provider provider, ChatRequest request, Consumer<String> onChunk) {
        String reply = buildReply(request);
        // 按字符逐个推送，模拟流式
        for (String ch : reply.split("")) {
            onChunk.accept(ch);
            try { Thread.sleep(18); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        return ChatResponse.of(reply, "stop", reply.length() / 2);
    }

    /**
     * 根据 messages 构造 mock 回复：
     * - system prompt 包含【参考资料】时，提取第 [1] 条内容作为引用依据
     * - 否则返回通用 mock 回复
     */
    private String buildReply(ChatRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return "Mock 回复：收到您的消息。";
        }

        // 找 system message
        String systemContent = request.getMessages().stream()
                .filter(m -> "system".equals(m.getRole()))
                .map(ChatMessage::getContent)
                .findFirst()
                .orElse("");

        // 找用户消息
        String userContent = request.getMessages().stream()
                .filter(m -> "user".equals(m.getRole()))
                .reduce((first, second) -> second)  // 取最后一条
                .map(ChatMessage::getContent)
                .orElse("");

        // 有 RAG 参考资料
        if (systemContent.contains("【参考资料】")) {
            // 提取 [1] 后面的内容作为引用
            String ref = extractRef(systemContent, 1);
            if (ref != null && !ref.isBlank()) {
                return String.format(
                    "根据知识库参考资料 [1]，%s\n\n（Mock 模式：真实环境将由 LLM 基于以上资料生成回答）",
                    ref.length() > 80 ? ref.substring(0, 80) + "…" : ref
                );
            }
            return "根据知识库资料，我没有找到与您问题直接相关的信息。建议联系人工客服进一步确认。\n\n（Mock 模式）";
        }

        // 无 RAG，普通 mock
        return String.format("收到您的问题：「%s」。这是 Mock 回复，真实环境会调用 LLM 生成答案。",
                userContent.length() > 20 ? userContent.substring(0, 20) + "…" : userContent);
    }

    /** 提取 system prompt 里第 n 条参考资料内容（[n] 到下一个 [n+1] 或末尾） */
    private String extractRef(String systemContent, int n) {
        String marker = "[" + n + "] ";
        int start = systemContent.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = systemContent.indexOf("[" + (n + 1) + "] ", start);
        return (end > start ? systemContent.substring(start, end) : systemContent.substring(start)).trim();
    }
}
