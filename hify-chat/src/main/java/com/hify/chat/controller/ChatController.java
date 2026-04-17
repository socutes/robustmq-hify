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
     * 流式对话，返回 SSE 事件流。
     */
    @PostMapping(value = "/sessions/{sessionId}/messages/stream", produces = "text/event-stream")
    public SseEmitter streamMessage(@PathVariable Long sessionId,
                                    @Valid @RequestBody SendMessageRequest request) {
        return chatService.streamChat(sessionId, request);
    }

    /**
     * 同步对话，返回完整回复。
     */
    @PostMapping("/sessions/{sessionId}/messages")
    public Result<MessageResp> sendMessage(@PathVariable Long sessionId,
                                           @Valid @RequestBody SendMessageRequest request) {
        if (request.isStream()) {
            // 兼容旧调用：stream=true 但打到了同步接口，重定向逻辑在此
            // 建议前端改用 /messages/stream 接口
            return Result.ok(chatService.syncChat(sessionId, request));
        }
        return Result.ok(chatService.syncChat(sessionId, request));
    }
}
