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
        // 有 tools 且用户问题匹配 → 模拟 tool_calls
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            String userMsg = lastUserMessage(request);
            String matchedTool = matchTool(request.getTools(), userMsg);
            if (matchedTool != null) {
                ChatResponse resp = new ChatResponse();
                resp.setFinishReason("tool_calls");
                resp.setContent(null);
                resp.setToolCalls(List.of(ChatResponse.ToolCall.of(
                        "call_mock_001", matchedTool,
                        buildMockArgs(matchedTool, userMsg))));
                return resp;
            }
        }
        String reply = buildReply(request);
        return ChatResponse.of(reply, "stop", reply.length() / 2);
    }

    @Override
    public ChatResponse streamChat(Provider provider, ChatRequest request, Consumer<String> onChunk) {
        // 有 tools 且匹配 → 模拟 tool_calls（不流式推送，直接返回）
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            String userMsg = lastUserMessage(request);
            String matchedTool = matchTool(request.getTools(), userMsg);
            if (matchedTool != null) {
                ChatResponse resp = new ChatResponse();
                resp.setFinishReason("tool_calls");
                resp.setContent(null);
                resp.setToolCalls(List.of(ChatResponse.ToolCall.of(
                        "call_mock_001", matchedTool,
                        buildMockArgs(matchedTool, userMsg))));
                return resp;
            }
        }
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

    /** 取最后一条 user 消息内容 */
    private String lastUserMessage(ChatRequest request) {
        if (request.getMessages() == null) return "";
        return request.getMessages().stream()
                .filter(m -> "user".equals(m.getRole()))
                .reduce((a, b) -> b)
                .map(ChatMessage::getContent)
                .orElse("");
    }

    /**
     * 判断用户消息是否匹配某个工具，返回工具名，无匹配返回 null。
     * 匹配逻辑：工具 description 里的关键词出现在用户消息里。
     */
    @SuppressWarnings("unchecked")
    private String matchTool(List<java.util.Map<String, Object>> tools, String userMsg) {
        if (userMsg == null || userMsg.isBlank()) return null;
        String lower = userMsg.toLowerCase();
        // 关键词触发：只要问题包含"订单"、"物流"、"快递"、"库存"、"工单"等就触发第一个工具
        boolean hasQueryKeyword = lower.contains("订单") || lower.contains("物流")
                || lower.contains("快递") || lower.contains("库存")
                || lower.contains("工单") || lower.contains("查")
                || lower.contains("到哪") || lower.contains("状态");
        if (!hasQueryKeyword) return null;
        // 返回第一个 tools 列表里的工具名
        for (java.util.Map<String, Object> tool : tools) {
            Object fn = tool.get("function");
            if (fn instanceof java.util.Map) {
                Object name = ((java.util.Map<String, Object>) fn).get("name");
                if (name != null) return name.toString();
            }
        }
        return null;
    }

    /** 根据工具名构建 mock 参数 JSON 字符串 */
    private String buildMockArgs(String toolName, String userMsg) {
        // 从用户消息里尝试提取数字作为 orderId
        String orderId = userMsg.replaceAll("[^0-9]", "");
        if (orderId.isBlank()) orderId = "MOCK-001";
        return String.format("{\"orderId\":\"%s\",\"userId\":\"mock-user\"}", orderId);
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
