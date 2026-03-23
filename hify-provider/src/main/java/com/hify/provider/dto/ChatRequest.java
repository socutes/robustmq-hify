package com.hify.provider.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ChatRequest {

    /** 调用 API 时使用的模型标识（来自 model_config.model_id） */
    private String modelId;

    /**
     * 对话消息列表。
     * system prompt 由 ChatService 在调用前作为第一条 system 消息插入。
     */
    private List<ChatMessage> messages;

    /** temperature，0.00~1.00 */
    private BigDecimal temperature;

    /** 最大输出 token 数 */
    private Integer maxTokens;

    /** 是否流式，ChatService 调用 streamChat 时始终为 true */
    @Builder.Default
    private boolean stream = false;
}
