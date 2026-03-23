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

    public static ChatResponse error(String message) {
        ChatResponse r = new ChatResponse();
        r.setContent("");
        r.setFinishReason("error");
        return r;
    }
}
