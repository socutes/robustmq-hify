package com.hify.chat.dto;

import com.hify.chat.entity.ChatSession;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SessionResp {

    private Long id;
    private Long agentId;
    private String title;
    private String status;
    private LocalDateTime createdAt;

    public static SessionResp from(ChatSession s) {
        SessionResp r = new SessionResp();
        r.id = s.getId();
        r.agentId = s.getAgentId();
        r.title = s.getTitle() != null ? s.getTitle() : "";
        r.status = s.getStatus();
        r.createdAt = s.getCreatedAt();
        return r;
    }
}
