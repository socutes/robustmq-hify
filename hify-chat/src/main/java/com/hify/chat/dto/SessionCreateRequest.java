package com.hify.chat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SessionCreateRequest {

    @NotNull(message = "agentId 不能为空")
    private Long agentId;

    private String title;
}
