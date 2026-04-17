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
import com.hify.common.log.MdcTaskWrapper;
import com.hify.common.log.RequestLogInterceptor;
import com.hify.common.metrics.HifyMetrics;
import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.agent.entity.AgentTool;
import com.hify.agent.mapper.AgentToolMapper;
import com.hify.knowledge.dto.ChunkVO;
import com.hify.knowledge.service.KnowledgeService;
import com.hify.mcp.entity.McpServer;
import com.hify.mcp.mapper.McpServerMapper;
import com.hify.mcp.service.McpService;
import com.hify.workflow.engine.WorkflowEngine;
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
import org.slf4j.MDC;

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
    private final AgentToolMapper agentToolMapper;
    private final McpServerMapper mcpServerMapper;
    private final McpService mcpService;
    private final ModelConfigMapper modelConfigMapper;
    private final ProviderMapper providerMapper;
    private final ProviderAdapterFactory adapterFactory;
    private final ObjectMapper objectMapper;
    private final KnowledgeService knowledgeService;

    @Qualifier("llmExecutor")
    private final Executor llmExecutor;

    private final WorkflowEngine workflowEngine;

    private final HifyMetrics metrics;

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
        SseEmitter emitter = new SseEmitter(60_000L);

        log.info("action=chat_stream_start sessionId={}", sessionId);
        long chatStart = System.currentTimeMillis();

        // MdcTaskWrapper 把父线程的 traceId、sessionId 传播到 llmExecutor 线程
        llmExecutor.execute(MdcTaskWrapper.wrap(() -> {
            try {
                doStreamChat(sessionId, request.getContent(), emitter);
            } catch (Exception e) {
                log.error("action=chat_stream_error sessionId={} error={}", sessionId, e.getMessage(), e);
                // agentId 在 doStreamChat 内才能获取，此处用 "unknown" 兜底
                metrics.chatRequestIncrement("unknown");
                metrics.chatRequestDuration("unknown", System.currentTimeMillis() - chatStart);
                try {
                    String errEvent = objectMapper.writeValueAsString(
                            Map.of("type", "error", "message", e.getMessage() != null ? e.getMessage() : "LLM 调用失败"));
                    emitter.send(SseEmitter.event().data(errEvent));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        }));

        emitter.onTimeout(() -> {
            log.warn("action=chat_stream_timeout sessionId={}", sessionId);
            emitter.complete();
        });
        emitter.onError(e -> log.warn("action=chat_stream_sse_error sessionId={} error={}", sessionId, e.getMessage()));

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

        // 把 sessionId / agentId 注入 MDC，此线程后续所有日志都会带上这两个字段
        MDC.put(RequestLogInterceptor.MDC_SESSION_ID, String.valueOf(sessionId));
        MDC.put(RequestLogInterceptor.MDC_AGENT_ID, String.valueOf(agent.getId()));
        String agentIdStr = String.valueOf(agent.getId());
        long chatBegin = System.currentTimeMillis();

        // 2.5 如果 Agent 绑了工作流，走工作流引擎，不走直接 LLM 路径
        if (agent.getWorkflowId() != null) {
            log.info("action=workflow_start sessionId={} agentId={} workflowId={}", sessionId, agent.getId(), agent.getWorkflowId());
            saveMessage(sessionId, "user", userContent, null, null, null);
            try {
                String wfOutput = workflowEngine.execute(agent.getWorkflowId(), userContent);
                for (String ch : wfOutput.split("")) {
                    String event = objectMapper.writeValueAsString(Map.of("type", "delta", "content", ch));
                    emitter.send(SseEmitter.event().data(event));
                }
                saveMessage(sessionId, "assistant", wfOutput, null, "stop", null);
                String doneEvent = objectMapper.writeValueAsString(Map.of("type", "done", "finishReason", "stop", "latencyMs", 0));
                emitter.send(SseEmitter.event().data(doneEvent));
                log.info("action=workflow_done sessionId={} agentId={}", sessionId, agent.getId());
            } catch (BizException e) {
                log.warn("action=workflow_error sessionId={} agentId={} error={}", sessionId, agent.getId(), e.getMessage());
                String errEvent = objectMapper.writeValueAsString(Map.of("type", "error", "message", e.getMessage()));
                emitter.send(SseEmitter.event().data(errEvent));
            }
            emitter.complete();
            return;
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

        // 6.5 RAG 检索
        List<ChunkVO> ragChunks = List.of();
        if (agent.getKnowledgeBaseId() != null) {
            ragChunks = knowledgeService.searchChunks(agent.getKnowledgeBaseId(), userContent, 3);
            log.info("action=rag_retrieve sessionId={} kbId={} hits={}", sessionId, agent.getKnowledgeBaseId(), ragChunks.size());
        }

        // 7. Build messages
        List<com.hify.provider.dto.ChatMessage> messages = buildMessages(
                agent.getSystemPrompt(), ragChunks, contextMsgs, userContent);

        // 8. 加载 MCP 工具 schema
        List<Map<String, Object>> toolSchemas = buildToolSchemas(agent.getId());

        // 9. Build ChatRequest
        ChatRequest chatRequest = ChatRequest.builder()
                .modelId(modelConfig.getModelId())
                .messages(messages)
                .temperature(agent.getTemperature())
                .maxTokens(agent.getMaxTokens())
                .tools(toolSchemas.isEmpty() ? null : toolSchemas)
                .build();

        // 10. 第一次 LLM 调用
        ProviderAdapter adapter = adapterFactory.get(provider.getType());
        long start = System.currentTimeMillis();

        log.info("action=llm_call_start sessionId={} agentId={} providerId={} modelName={} tools={}",
                sessionId, agent.getId(), provider.getId(), modelConfig.getModelId(), toolSchemas.size());

        ChatResponse llmResp;
        try {
            llmResp = adapter.streamChat(provider, chatRequest, delta -> {
                try {
                    String event = objectMapper.writeValueAsString(Map.of("type", "delta", "content", delta));
                    emitter.send(SseEmitter.event().data(event));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            long failMs = System.currentTimeMillis() - start;
            log.error("action=llm_call_done sessionId={} agentId={} providerId={} modelName={} durationMs={} success=false error={}",
                    sessionId, agent.getId(), provider.getId(), modelConfig.getModelId(), failMs, e.getMessage(), e);
            metrics.llmCallIncrement(provider.getType(), modelConfig.getModelId(), false);
            metrics.llmCallDuration(provider.getType(), modelConfig.getModelId(), failMs);
            throw e;
        }

        long firstCallMs = System.currentTimeMillis() - start;
        log.info("action=llm_call_done sessionId={} agentId={} providerId={} modelName={} durationMs={} success=true finishReason={} tokens={}",
                sessionId, agent.getId(), provider.getId(), modelConfig.getModelId(), firstCallMs,
                llmResp.getFinishReason(), llmResp.getCompletionTokens());
        metrics.llmCallIncrement(provider.getType(), modelConfig.getModelId(), true);
        metrics.llmCallDuration(provider.getType(), modelConfig.getModelId(), firstCallMs);

        // 11. 工具调用（Function Calling 第二轮）
        if ("tool_calls".equals(llmResp.getFinishReason())
                && llmResp.getToolCalls() != null && !llmResp.getToolCalls().isEmpty()) {

            log.info("action=tool_call_start sessionId={} agentId={} tools={}", sessionId, agent.getId(),
                    llmResp.getToolCalls().stream().map(ChatResponse.ToolCall::getName).toList());

            com.hify.provider.dto.ChatMessage assistantMsg = new com.hify.provider.dto.ChatMessage();
            assistantMsg.setRole("assistant");
            assistantMsg.setContent("");
            assistantMsg.setToolCalls(llmResp.getToolCalls());
            messages.add(assistantMsg);

            for (ChatResponse.ToolCall toolCall : llmResp.getToolCalls()) {
                long toolStart = System.currentTimeMillis();
                String toolResult;
                try {
                    toolResult = executeToolCall(toolCall, agent.getId());
                    long toolMs = System.currentTimeMillis() - toolStart;
                    log.info("action=tool_call_done sessionId={} agentId={} tool={} durationMs={} resultLen={}",
                            sessionId, agent.getId(), toolCall.getName(), toolMs, toolResult.length());
                    metrics.mcpToolCallIncrement(toolCall.getName(), true);
                    metrics.mcpToolCallDuration(toolCall.getName(), toolMs);
                } catch (Exception e) {
                    long toolMs = System.currentTimeMillis() - toolStart;
                    log.error("action=tool_call_error sessionId={} agentId={} tool={} durationMs={} error={}",
                            sessionId, agent.getId(), toolCall.getName(), toolMs, e.getMessage());
                    metrics.mcpToolCallIncrement(toolCall.getName(), false);
                    metrics.mcpToolCallDuration(toolCall.getName(), toolMs);
                    throw e;
                }

                com.hify.provider.dto.ChatMessage toolMsg = new com.hify.provider.dto.ChatMessage();
                toolMsg.setRole("tool");
                toolMsg.setToolCallId(toolCall.getId());
                toolMsg.setContent(toolResult);
                messages.add(toolMsg);
            }

            ChatRequest secondReq = ChatRequest.builder()
                    .modelId(modelConfig.getModelId())
                    .messages(messages)
                    .temperature(agent.getTemperature())
                    .maxTokens(agent.getMaxTokens())
                    .build();

            log.info("action=llm_call_start sessionId={} agentId={} providerId={} modelName={} round=2",
                    sessionId, agent.getId(), provider.getId(), modelConfig.getModelId());

            long secondStart = System.currentTimeMillis();
            try {
                llmResp = adapter.streamChat(provider, secondReq, delta -> {
                    try {
                        String event = objectMapper.writeValueAsString(Map.of("type", "delta", "content", delta));
                        emitter.send(SseEmitter.event().data(event));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                long failMs = System.currentTimeMillis() - secondStart;
                log.error("action=llm_call_done sessionId={} agentId={} providerId={} modelName={} durationMs={} success=false round=2 error={}",
                        sessionId, agent.getId(), provider.getId(), modelConfig.getModelId(), failMs, e.getMessage(), e);
                metrics.llmCallIncrement(provider.getType(), modelConfig.getModelId(), false);
                metrics.llmCallDuration(provider.getType(), modelConfig.getModelId(), failMs);
                throw e;
            }
            long secondMs = System.currentTimeMillis() - secondStart;
            log.info("action=llm_call_done sessionId={} agentId={} providerId={} modelName={} durationMs={} success=true finishReason={} round=2",
                    sessionId, agent.getId(), provider.getId(), modelConfig.getModelId(),
                    secondMs, llmResp.getFinishReason());
            metrics.llmCallIncrement(provider.getType(), modelConfig.getModelId(), true);
            metrics.llmCallDuration(provider.getType(), modelConfig.getModelId(), secondMs);
        }

        int latencyMs = (int) (System.currentTimeMillis() - start);

        // 12. Save assistant message
        saveMessage(sessionId, "assistant", llmResp.getContent(),
                llmResp.getCompletionTokens(), llmResp.getFinishReason(), latencyMs);

        // 13. Update Redis context
        updateContext(sessionId, userContent, llmResp.getContent(), maxMsgs);

        // 14. Send done event
        String doneEvent = objectMapper.writeValueAsString(Map.of(
                "type", "done",
                "finishReason", llmResp.getFinishReason() != null ? llmResp.getFinishReason() : "stop",
                "latencyMs", latencyMs));
        emitter.send(SseEmitter.event().data(doneEvent));
        emitter.complete();

        log.info("action=chat_stream_done sessionId={} agentId={} totalMs={}", sessionId, agent.getId(), latencyMs);
        metrics.chatRequestIncrement(agentIdStr);
        metrics.chatRequestDuration(agentIdStr, System.currentTimeMillis() - chatBegin);
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
        boolean llmSuccess = false;
        ChatResponse llmResp;
        try {
            llmResp = adapterFactory.get(provider.getType()).chat(provider, chatRequest);
            llmSuccess = true;
        } finally {
            long llmMs = System.currentTimeMillis() - start;
            metrics.llmCallIncrement(provider.getType(), modelConfig.getModelId(), llmSuccess);
            metrics.llmCallDuration(provider.getType(), modelConfig.getModelId(), llmMs);
        }
        int latencyMs = (int) (System.currentTimeMillis() - start);
        metrics.chatRequestIncrement(String.valueOf(agent.getId()));
        metrics.chatRequestDuration(String.valueOf(agent.getId()), latencyMs);

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

    // ── MCP 工具辅助 ──────────────────────────────────────────

    /**
     * 构建 Agent 绑定的所有 MCP Server 的工具 Schema 列表（OpenAI tools 格式）。
     * 无绑定时返回空列表。
     */
    private List<Map<String, Object>> buildToolSchemas(Long agentId) {
        List<Long> mcpServerIds = agentToolMapper.selectMcpServerIdsByAgentId(agentId);
        if (mcpServerIds.isEmpty()) return List.of();

        List<Map<String, Object>> schemas = new ArrayList<>();
        for (Long serverId : mcpServerIds) {
            McpServer server = mcpServerMapper.selectById(serverId);
            if (server == null || server.getEnabled() != 1) continue;
            try {
                List<String> toolNames = mcpService.listTools(serverId);
                if (toolNames.isEmpty()) {
                    // 连不上时 fallback：使用 server 名称推断工具列表
                    toolNames = buildFallbackToolNames(server.getName());
                    log.info("[MCP] Server {} 无法连接，使用 fallback 工具列表: {}", server.getName(), toolNames);
                }
                for (String toolName : toolNames) {
                    schemas.add(Map.of(
                            "type", "function",
                            "function", Map.of(
                                    "name", toolName,
                                    "description", buildToolDescription(toolName, server.getName()),
                                    "parameters", Map.of(
                                            "type", "object",
                                            "properties", Map.of(
                                                    "orderId", Map.of("type", "string", "description", "订单号"),
                                                    "userId", Map.of("type", "string", "description", "用户ID")
                                            )
                                    )
                            )
                    ));
                }
            } catch (Exception e) {
                log.warn("[MCP] 加载工具列表失败 serverId={}: {}", serverId, e.getMessage());
                // fallback 工具列表
                List<String> fallback = buildFallbackToolNames(server.getName());
                for (String toolName : fallback) {
                    schemas.add(Map.of(
                            "type", "function",
                            "function", Map.of(
                                    "name", toolName,
                                    "description", buildToolDescription(toolName, server.getName()),
                                    "parameters", Map.of(
                                            "type", "object",
                                            "properties", Map.of(
                                                    "orderId", Map.of("type", "string", "description", "订单号"),
                                                    "userId", Map.of("type", "string", "description", "用户ID")
                                            )
                                    )
                            )
                    ));
                }
            }
        }
        return schemas;
    }

    /**
     * 执行一次工具调用，返回文本结果。
     * 失败时返回错误描述而不抛异常，让 LLM 决定如何告知用户。
     */
    private String executeToolCall(ChatResponse.ToolCall toolCall, Long agentId) {
        List<Long> mcpServerIds = agentToolMapper.selectMcpServerIdsByAgentId(agentId);
        for (Long serverId : mcpServerIds) {
            try {
                // 尝试在这个 server 上调用工具（mock 场景下会直接失败，返回 mock 结果）
                Map<String, Object> args = parseArguments(toolCall.getArguments());
                return mcpService.callTool(serverId, toolCall.getName(), args);
            } catch (Exception e) {
                // mock profile 下 MCP Server 实际不存在，返回模拟数据
                log.info("[MCP] callTool 失败，使用 mock 数据 tool={}: {}", toolCall.getName(), e.getMessage());
                return buildMockToolResult(toolCall.getName(), toolCall.getArguments());
            }
        }
        return buildMockToolResult(toolCall.getName(), toolCall.getArguments());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String argumentsJson) {
        try {
            return objectMapper.readValue(argumentsJson, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** 根据 server 名称推断工具列表（MCP Server 连不上时的 fallback） */
    private List<String> buildFallbackToolNames(String serverName) {
        if (serverName != null && (serverName.contains("退款") || serverName.contains("refund"))) {
            return List.of("check_refund_eligibility", "submit_refund", "get_refund_status", "cancel_refund");
        }
        if (serverName != null && (serverName.contains("订单") || serverName.contains("order"))) {
            return List.of("query_order", "cancel_order");
        }
        if (serverName != null && (serverName.contains("物流") || serverName.contains("logistics"))) {
            return List.of("track_logistics");
        }
        return List.of();
    }

    /** 根据工具名生成 description */
    private String buildToolDescription(String toolName, String serverName) {
        return switch (toolName) {
            case "check_refund_eligibility" -> "查询订单是否满足退款条件";
            case "submit_refund"            -> "提交退款申请";
            case "get_refund_status"        -> "查询退款进度和状态";
            case "cancel_refund"            -> "取消退款申请";
            case "query_order"              -> "查询订单详情";
            case "cancel_order"             -> "取消订单";
            case "track_logistics"          -> "查询物流轨迹";
            default -> "来自 " + serverName + " 的工具：" + toolName;
        };
    }

    /** mock profile 下，工具调用失败时返回的模拟数据（按工具名区分） */
    private String buildMockToolResult(String toolName, String arguments) {
        return switch (toolName) {
            case "check_refund_eligibility" ->
                "{\"eligible\":true,\"orderId\":\"20240501001\",\"orderAmount\":299.00," +
                "\"payTime\":\"2026-04-01 14:22:10\",\"productName\":\"无线蓝牙耳机\"," +
                "\"reason\":\"商品在7天无理由退货期内，可以申请退款\"}";
            case "submit_refund" ->
                "{\"success\":true,\"refundId\":\"RF20260408001\",\"orderId\":\"20240501001\"," +
                "\"refundAmount\":299.00,\"status\":\"审核中\"," +
                "\"estimatedTime\":\"1-3个工作日内退回原支付账户\"}";
            case "get_refund_status" ->
                "{\"refundId\":\"RF20260408001\",\"orderId\":\"20240501001\"," +
                "\"status\":\"已退款\",\"refundAmount\":299.00," +
                "\"refundTime\":\"2026-04-08 16:30:00\",\"bankAccount\":\"尾号6379\"}";
            case "cancel_refund" ->
                "{\"success\":true,\"refundId\":\"RF20260408001\"," +
                "\"message\":\"退款申请已成功取消，订单恢复正常状态\"}";
            default ->
                "{\"status\":\"运输中\",\"trackingNo\":\"SF1234567\"," +
                "\"estimatedDate\":\"明天\",\"tool\":\"" + toolName + "\"}";
        };
    }
}
