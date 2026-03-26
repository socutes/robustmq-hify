package com.hify.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendMessageRequest {

    @NotBlank(message = "消息内容不能为空")
    private String content;

    /** true = SSE streaming; false = sync (default true) */
    private boolean stream = true;
}
