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
     * 根据 messages 构造回复：
     * - system prompt 包含【参考资料】时，基于资料内容生成专业回答
     * - 否则基于用户问题生成通用回答
     */
    private String buildReply(ChatRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return "您好，请问有什么可以帮助您的？";
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
                .reduce((first, second) -> second)
                .map(ChatMessage::getContent)
                .orElse("");

        // 有 RAG 参考资料 —— 提取所有可用资料，整合成完整回答
        if (systemContent.contains("【参考资料】")) {
            StringBuilder answer = new StringBuilder();
            List<String> refs = new java.util.ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                String ref = extractRef(systemContent, i);
                if (ref == null || ref.isBlank()) break;
                refs.add(ref);
            }
            if (!refs.isEmpty()) {
                // 将所有参考资料内容拼接成回答正文
                answer.append("根据我们的相关资料，为您解答如下：\n\n");
                for (String ref : refs) {
                    answer.append(ref.trim()).append("\n\n");
                }
                answer.append("如有其他疑问，欢迎继续提问。");
                return answer.toString().trim();
            }
            return "根据现有资料，暂未找到与您问题直接相关的信息。建议您联系人工客服进一步确认，感谢您的理解。";
        }

        // 无 RAG，基于问题给通用回答
        return String.format("您好！关于您提到的「%s」，这是一个很好的问题。如需获取更准确的信息，建议您查阅相关文档或联系专业支持团队。",
                userContent.length() > 30 ? userContent.substring(0, 30) + "…" : userContent);
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
