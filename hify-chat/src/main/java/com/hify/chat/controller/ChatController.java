package com.hify.chat.controller;

import com.hify.chat.dto.*;
import com.hify.chat.service.ChatService;
import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // ── 会话 ────────────────────────────────────────────────

    @PostMapping("/sessions")
    public Result<SessionResp> createSession(@Valid @RequestBody SessionCreateRequest request) {
        return Result.ok(chatService.createSession(request));
    }

    @GetMapping("/sessions")
    public Result<PageResult<SessionResp>> listSessions(
            @RequestParam(required = false) Long agentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return chatService.listSessions(agentId, page, pageSize);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
        return Result.ok();
    }

    // ── 消息 ────────────────────────────────────────────────

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<PageResult<MessageResp>> listMessages(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return chatService.listMessages(sessionId, page, pageSize);
    }

    /**
     * 发送消息。
     * stream=true  → Accept: text/event-stream，返回 SseEmitter
     * stream=false → 返回 Result<MessageResp>
     */
    @PostMapping(value = "/sessions/{sessionId}/messages", produces = {"text/event-stream", "application/json"})
    public Object sendMessage(@PathVariable Long sessionId,
                              @Valid @RequestBody SendMessageRequest request) {
        if (request.isStream()) {
            return chatService.streamChat(sessionId, request);
        } else {
            return Result.ok(chatService.syncChat(sessionId, request));
        }
    }
}
