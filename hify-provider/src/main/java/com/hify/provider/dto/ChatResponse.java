package com.hify.provider.dto;

import lombok.Data;

@Data
public class ChatResponse {

    /** 模型返回的完整文本内容 */
    private String content;

    /**
     * 结束原因：
     *   stop   — 正常结束
     *   length — 达到 max_tokens 截断
     *   error  — 调用异常
     */
    private String finishReason;

    /** 输出 token 数（部分供应商会返回，Ollama 不返回） */
    private Integer completionTokens;

    public static ChatResponse of(String content, String finishReason, Integer completionTokens) {
        ChatResponse r = new ChatResponse();
        r.setContent(content);
        r.setFinishReason(finishReason);
        r.setCompletionTokens(completionTokens);
        return r;
    }

    /**
     * tool_calls：当 finishReason = "tool_calls" 时非空。
     * 每个元素结构：{ id, name, arguments (JSON string) }
     */
    private java.util.List<ToolCall> toolCalls;

    @lombok.Data
    public static class ToolCall {
        private String id;
        private String name;
        /** arguments JSON 字符串，如 {"orderId":"12345"} */
        private String arguments;

        public static ToolCall of(String id, String name, String arguments) {
            ToolCall tc = new ToolCall();
            tc.id = id;
            tc.name = name;
            tc.arguments = arguments;
            return tc;
        }
    }

    public static ChatResponse error(String message) {
        ChatResponse r = new ChatResponse();
        r.setContent("");
        r.setFinishReason("error");
        return r;
    }
}
