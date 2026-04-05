package com.hify.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.agent.entity.Agent;
import com.hify.agent.mapper.AgentMapper;
import com.hify.chat.dto.*;
import com.hify.chat.entity.ChatMessage;
import com.hify.chat.entity.ChatSession;
import com.hify.chat.mapper.ChatMessageMapper;
import com.hify.chat.mapper.ChatSessionMapper;
import com.hify.chat.service.ChatService;
import com.hify.common.config.RedisUtil;
import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.knowledge.dto.ChunkVO;
import com.hify.knowledge.service.KnowledgeService;
import com.hify.provider.adapter.ProviderAdapter;
import com.hify.provider.adapter.ProviderAdapterFactory;
import com.hify.provider.dto.ChatRequest;
import com.hify.provider.dto.ChatResponse;
import com.hify.provider.entity.ModelConfig;
import com.hify.provider.entity.Provider;
import com.hify.provider.mapper.ModelConfigMapper;
import com.hify.provider.mapper.ProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final AgentMapper agentMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final ProviderMapper providerMapper;
    private final ProviderAdapterFactory adapterFactory;
    private final ObjectMapper objectMapper;
    private final KnowledgeService knowledgeService;

    @Qualifier("llmExecutor")
    private final Executor llmExecutor;

    // Redis is optional — null in mock profile
    private final Optional<RedisUtil> redisUtil;

    private static final Duration SESSION_TTL = Duration.ofHours(2);

    // ── 会话管理 ──────────────────────────────────────────────

    @Override
    @Transactional
    public SessionResp createSession(SessionCreateRequest request) {
        Agent agent = agentMapper.selectById(request.getAgentId());
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }
        ChatSession session = new ChatSession();
        session.setAgentId(request.getAgentId());
        session.setTitle(request.getTitle() != null ? request.getTitle() : "");
        session.setStatus("ACTIVE");
        sessionMapper.insert(session);
        return SessionResp.from(session);
    }

    @Override
    public Result<PageResult<SessionResp>> listSessions(Long agentId, int page, int pageSize) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .eq(agentId != null, ChatSession::getAgentId, agentId)
                .orderByDesc(ChatSession::getCreatedAt);
        var p = sessionMapper.selectPage(new Page<>(page, Math.min(pageSize, 100)), wrapper);
        List<SessionResp> list = p.getRecords().stream().map(SessionResp::from).collect(Collectors.toList());
        return PageResult.of(list, p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
    }

    @Override
    @Transactional
    public void deleteSession(Long sessionId) {
        ChatSession session = getSessionOrThrow(sessionId);
        sessionMapper.deleteById(session.getId());
        // clean context cache
        redisUtil.ifPresent(r -> r.delete(contextKey(sessionId)));
    }

    @Override
    public Result<PageResult<MessageResp>> listMessages(Long sessionId, int page, int pageSize) {
        getSessionOrThrow(sessionId);
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreatedAt);
        var p = messageMapper.selectPage(new Page<>(page, Math.min(pageSize, 100)), wrapper);
        List<MessageResp> list = p.getRecords().stream().map(MessageResp::from).collect(Collectors.toList());
        return PageResult.of(list, p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
    }

    // ── 流式对话 ──────────────────────────────────────────────

    @Override
    public SseEmitter streamChat(Long sessionId, SendMessageRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L); // 60s timeout

        llmExecutor.execute(() -> {
            try {
                doStreamChat(sessionId, request.getContent(), emitter);
            } catch (Exception e) {
                log.error("streamChat error session={}: {}", sessionId, e.getMessage());
                try {
                    String errEvent = objectMapper.writeValueAsString(
                            Map.of("type", "error", "message", e.getMessage() != null ? e.getMessage() : "LLM 调用失败"));
                    emitter.send(SseEmitter.event().data(errEvent));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> log.warn("SSE error session={}: {}", sessionId, e.getMessage()));

        return emitter;
    }

    private void doStreamChat(Long sessionId, String userContent, SseEmitter emitter) throws Exception {
        // 1. Load session
        ChatSession session = getSessionOrThrow(sessionId);

        // 2. Load Agent
        Agent agent = agentMapper.selectById(session.getAgentId());
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }

        // 3. Load ModelConfig
        ModelConfig modelConfig = modelConfigMapper.selectById(agent.getModelConfigId());
        if (modelConfig == null || modelConfig.getEnabled() != 1) {
            throw new IllegalStateException("模型配置不存在或已禁用: " + agent.getModelConfigId());
        }

        // 4. Load Provider
        Provider provider = providerMapper.selectById(modelConfig.getProviderId());
        if (provider == null || provider.getEnabled() != 1) {
            throw new IllegalStateException("提供商不存在或已禁用: " + modelConfig.getProviderId());
        }

        // 5. Save user message to MySQL
        saveMessage(sessionId, "user", userContent, null, null, null);

        // 6. Load context from Redis (on miss: load from MySQL)
        int maxMsgs = (agent.getMaxContextTurns() != null ? agent.getMaxContextTurns() : 10) * 2;
        List<Map<String, String>> contextMsgs = loadContext(sessionId, maxMsgs);

        // 6.5 RAG 检索：Agent 绑了知识库则检索，否则跳过
        List<ChunkVO> ragChunks = List.of();
        if (agent.getKnowledgeBaseId() != null) {
            ragChunks = knowledgeService.searchChunks(agent.getKnowledgeBaseId(), userContent, 3);
            log.info("RAG 检索命中 {} 条 sessionId={} kbId={}", ragChunks.size(), sessionId, agent.getKnowledgeBaseId());
        }

        // 7. Build messages array: system(+RAG) + context + current user message
        List<com.hify.provider.dto.ChatMessage> messages = buildMessages(
                agent.getSystemPrompt(), ragChunks, contextMsgs, userContent);

        // 8. Build ChatRequest
        ChatRequest chatRequest = ChatRequest.builder()
                .modelId(modelConfig.getModelId())
                .messages(messages)
                .temperature(agent.getTemperature())
                .maxTokens(agent.getMaxTokens())
                .build();

        // 9. Call LLM streaming
        ProviderAdapter adapter = adapterFactory.get(provider.getType());
        long start = System.currentTimeMillis();

        ChatResponse llmResp = adapter.streamChat(provider, chatRequest, delta -> {
            try {
                String event = objectMapper.writeValueAsString(Map.of("type", "delta", "content", delta));
                emitter.send(SseEmitter.event().data(event));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        int latencyMs = (int) (System.currentTimeMillis() - start);

        // 10. Save assistant message to MySQL
        saveMessage(sessionId, "assistant", llmResp.getContent(),
                llmResp.getCompletionTokens(), llmResp.getFinishReason(), latencyMs);

        // 11. Update Redis context
        updateContext(sessionId, userContent, llmResp.getContent(), maxMsgs);

        // 12. Send done event
        String doneEvent = objectMapper.writeValueAsString(Map.of(
                "type", "done",
                "finishReason", llmResp.getFinishReason() != null ? llmResp.getFinishReason() : "stop",
                "latencyMs", latencyMs));
        emitter.send(SseEmitter.event().data(doneEvent));
        emitter.complete();
    }

    // ── 同步对话 ──────────────────────────────────────────────

    @Override
    public MessageResp syncChat(Long sessionId, SendMessageRequest request) {
        ChatSession session = getSessionOrThrow(sessionId);
        Agent agent = agentMapper.selectById(session.getAgentId());
        if (agent == null) throw new BizException(ErrorCode.AGENT_NOT_FOUND);

        ModelConfig modelConfig = modelConfigMapper.selectById(agent.getModelConfigId());
        if (modelConfig == null || modelConfig.getEnabled() != 1) {
            throw new IllegalStateException("模型配置不存在或已禁用");
        }

        Provider provider = providerMapper.selectById(modelConfig.getProviderId());
        if (provider == null || provider.getEnabled() != 1) {
            throw new IllegalStateException("提供商不存在或已禁用");
        }

        String userContent = request.getContent();
        saveMessage(sessionId, "user", userContent, null, null, null);

        int maxMsgs = (agent.getMaxContextTurns() != null ? agent.getMaxContextTurns() : 10) * 2;
        List<Map<String, String>> contextMsgs = loadContext(sessionId, maxMsgs);

        List<ChunkVO> ragChunks = List.of();
        if (agent.getKnowledgeBaseId() != null) {
            ragChunks = knowledgeService.searchChunks(agent.getKnowledgeBaseId(), userContent, 3);
        }
        List<com.hify.provider.dto.ChatMessage> messages = buildMessages(
                agent.getSystemPrompt(), ragChunks, contextMsgs, userContent);

        ChatRequest chatRequest = ChatRequest.builder()
                .modelId(modelConfig.getModelId())
                .messages(messages)
                .temperature(agent.getTemperature())
                .maxTokens(agent.getMaxTokens())
                .build();

        long start = System.currentTimeMillis();
        ChatResponse llmResp = adapterFactory.get(provider.getType()).chat(provider, chatRequest);
        int latencyMs = (int) (System.currentTimeMillis() - start);

        ChatMessage saved = saveMessage(sessionId, "assistant", llmResp.getContent(),
                llmResp.getCompletionTokens(), llmResp.getFinishReason(), latencyMs);
        updateContext(sessionId, userContent, llmResp.getContent(), maxMsgs);
        return MessageResp.from(saved);
    }

    // ── 内部工具 ──────────────────────────────────────────────

    private ChatSession getSessionOrThrow(Long sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        return session;
    }

    @Transactional
    public ChatMessage saveMessage(Long sessionId, String role, String content,
                                   Integer tokens, String finishReason, Integer latencyMs) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content != null ? content : "");
        msg.setTokens(tokens != null ? tokens : 0);
        msg.setFinishReason(finishReason != null ? finishReason : "");
        msg.setLatencyMs(latencyMs != null ? latencyMs : 0);
        messageMapper.insert(msg);
        return msg;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> loadContext(Long sessionId, int maxMsgs) {
        String key = contextKey(sessionId);
        // Try Redis first
        if (redisUtil.isPresent()) {
            Optional<List<Map<String, String>>> cached = redisUtil.get().get(key);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        // Fall back to MySQL
        List<ChatMessage> dbMsgs = messageMapper.selectRecentBySessionId(sessionId, maxMsgs);
        List<Map<String, String>> ctx = dbMsgs.stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());
        // Write back to Redis
        redisUtil.ifPresent(r -> r.set(key, ctx, SESSION_TTL));
        return ctx;
    }

    private void updateContext(Long sessionId, String userContent, String assistantContent, int maxMsgs) {
        String key = contextKey(sessionId);
        redisUtil.ifPresent(r -> {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> ctx = r.<List<Map<String, String>>>get(key).orElseGet(ArrayList::new);
            ctx = new ArrayList<>(ctx);
            ctx.add(Map.of("role", "user", "content", userContent));
            ctx.add(Map.of("role", "assistant", "content", assistantContent != null ? assistantContent : ""));
            // Trim to rolling window
            if (ctx.size() > maxMsgs) {
                ctx = ctx.subList(ctx.size() - maxMsgs, ctx.size());
            }
            r.set(key, ctx, SESSION_TTL);
        });
    }

    private List<com.hify.provider.dto.ChatMessage> buildMessages(
            String systemPrompt,
            List<ChunkVO> ragChunks,
            List<Map<String, String>> contextMsgs,
            String userContent) {

        // 拼 system prompt：Agent 原始 Prompt + RAG 检索结果（如果有）
        String finalSystem = buildSystemPrompt(systemPrompt, ragChunks);

        List<com.hify.provider.dto.ChatMessage> msgs = new ArrayList<>();
        if (finalSystem != null && !finalSystem.isBlank()) {
            com.hify.provider.dto.ChatMessage sys = new com.hify.provider.dto.ChatMessage();
            sys.setRole("system");
            sys.setContent(finalSystem);
            msgs.add(sys);
        }
        for (Map<String, String> ctx : contextMsgs) {
            com.hify.provider.dto.ChatMessage m = new com.hify.provider.dto.ChatMessage();
            m.setRole(ctx.get("role"));
            m.setContent(ctx.get("content"));
            msgs.add(m);
        }
        com.hify.provider.dto.ChatMessage user = new com.hify.provider.dto.ChatMessage();
        user.setRole("user");
        user.setContent(userContent);
        msgs.add(user);
        return msgs;
    }

    /**
     * 拼接最终 system prompt。
     * - 没有 RAG chunk：直接返回 Agent 原始 prompt
     * - 有 RAG chunk：Agent prompt 保留，后面追加参考资料
     */
    private String buildSystemPrompt(String agentPrompt, List<ChunkVO> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return agentPrompt;
        }
        StringBuilder sb = new StringBuilder();
        if (agentPrompt != null && !agentPrompt.isBlank()) {
            sb.append(agentPrompt).append("\n\n");
        }
        sb.append("请基于以下参考资料回答用户问题。");
        sb.append("如果资料中没有相关信息，直接说「我没有找到相关资料」，不要编造。\n\n");
        sb.append("【参考资料】\n");
        for (int i = 0; i < chunks.size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(chunks.get(i).getContent()).append("\n");
        }
        return sb.toString();
    }

    private String contextKey(Long sessionId) {
        return "session:" + sessionId;
    }
}
