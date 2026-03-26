package com.hify.chat.service;

import com.hify.chat.dto.*;
import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {

    SessionResp createSession(SessionCreateRequest request);

    Result<PageResult<SessionResp>> listSessions(Long agentId, int page, int pageSize);

    void deleteSession(Long sessionId);

    Result<PageResult<MessageResp>> listMessages(Long sessionId, int page, int pageSize);

    /** 流式对话，返回 SseEmitter（立即返回，后台异步推送） */
    SseEmitter streamChat(Long sessionId, SendMessageRequest request);

    /** 同步对话，返回完整消息 */
    MessageResp syncChat(Long sessionId, SendMessageRequest request);
}
