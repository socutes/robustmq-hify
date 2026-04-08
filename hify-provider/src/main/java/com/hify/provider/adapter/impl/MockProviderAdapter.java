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

        // 有 tool 调用结果 —— 生成自然语言回答
        String toolResult = request.getMessages().stream()
                .filter(m -> "tool".equals(m.getRole()))
                .reduce((a, b) -> b)
                .map(ChatMessage::getContent)
                .orElse(null);
        if (toolResult != null) {
            return buildToolReply(toolResult, userContent);
        }

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
                || lower.contains("到哪") || lower.contains("状态")
                || lower.contains("退款") || lower.contains("退货")
                || lower.contains("退钱") || lower.contains("能退") || lower.contains("申请退");
        if (!hasQueryKeyword) return null;
        // 根据用户意图选最匹配的工具
        String preferredTool = null;
        if (lower.contains("退款状态") || lower.contains("退款进度") || lower.contains("退到哪")) {
            preferredTool = "get_refund_status";
        } else if (lower.contains("申请退") || lower.contains("我要退") || lower.contains("帮我退")) {
            preferredTool = "submit_refund";
        } else if (lower.contains("能退") || lower.contains("可以退") || lower.contains("退款吗") || lower.contains("退货")) {
            preferredTool = "check_refund_eligibility";
        } else if (lower.contains("取消退")) {
            preferredTool = "cancel_refund";
        }

        // 先在 tools 列表里找 preferredTool，找不到就用第一个
        for (java.util.Map<String, Object> tool : tools) {
            Object fn = tool.get("function");
            if (fn instanceof java.util.Map<?, ?> fnMap) {
                Object name = fnMap.get("name");
                if (name != null && name.toString().equals(preferredTool)) return name.toString();
            }
        }
        for (java.util.Map<String, Object> tool : tools) {
            Object fn = tool.get("function");
            if (fn instanceof java.util.Map<?, ?> fnMap) {
                Object name = fnMap.get("name");
                if (name != null) return name.toString();
            }
        }
        return null;
    }

    /** 根据工具名构建 mock 参数 JSON 字符串 */
    private String buildMockArgs(String toolName, String userMsg) {
        String orderId = userMsg.replaceAll("[^0-9]", "");
        if (orderId.isBlank()) orderId = "20240501001";
        return String.format("{\"orderId\":\"%s\",\"userId\":\"mock-user\"}", orderId);
    }

    /** 把工具调用结果 JSON 转为自然语言回复 */
    private String buildToolReply(String toolResultJson, String userQuestion) {
        // check_refund_eligibility
        if (toolResultJson.contains("eligible")) {
            if (toolResultJson.contains("\"eligible\":true")) {
                return "好消息！我帮您查询了一下，您的订单 **20240501001**（无线蓝牙耳机，¥299.00）**符合退款条件**。\n\n" +
                       "该商品在7天无理由退货期内，您可以直接申请退款。\n\n" +
                       "请问需要我现在帮您提交退款申请吗？";
            } else {
                return "抱歉，经查询您的订单目前**不符合退款条件**，可能已超过退货期或属于不可退商品。\n\n" +
                       "如有疑问，建议联系人工客服进一步处理。";
            }
        }
        // submit_refund
        if (toolResultJson.contains("refundId") && toolResultJson.contains("审核中")) {
            return "退款申请已成功提交！\n\n" +
                   "- 退款单号：**RF20260408001**\n" +
                   "- 退款金额：**¥299.00**\n" +
                   "- 当前状态：审核中\n" +
                   "- 预计到账：**1-3个工作日**退回原支付账户\n\n" +
                   "退款到账后系统会短信通知您，请注意查收。";
        }
        // get_refund_status
        if (toolResultJson.contains("\"status\":\"已退款\"")) {
            return "您的退款已处理完成！\n\n" +
                   "- 退款单号：**RF20260408001**\n" +
                   "- 退款金额：**¥299.00**\n" +
                   "- 退款状态：**已退款** ✓\n" +
                   "- 退款时间：2026-04-08 16:30:00\n" +
                   "- 退款账户：尾号 **6379**\n\n" +
                   "如果长时间未到账，请联系您的银行确认。";
        }
        // cancel_refund
        if (toolResultJson.contains("已成功取消")) {
            return "您的退款申请已成功取消，订单已恢复正常状态。\n\n如后续还需要退款，随时告诉我即可。";
        }
        // 默认
        return "已查询到相关信息：\n\n```json\n" + toolResultJson + "\n```\n\n如需进一步操作，请告诉我。";
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
