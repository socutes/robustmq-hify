package com.hify.chat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("chat_message")
public class ChatMessage extends BaseEntity {

    private Long sessionId;

    /** user / assistant / system */
    private String role;

    private String content;

    private Integer tokens;

    private String finishReason;

    private Integer latencyMs;
}
