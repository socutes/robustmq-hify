package com.hify.common.metrics;

import io.micrometer.core.instrument.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一管理 Hify 所有 Prometheus 指标。
 * 命名规范：hify_{模块}_{动作}_{单位}
 */
@Component
public class HifyMetrics {

    private final MeterRegistry registry;

    // 熔断器状态缓存，避免重复注册
    private final ConcurrentHashMap<String, Gauge> circuitBreakerGauges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> circuitBreakerStates = new ConcurrentHashMap<>();

    public HifyMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 启动时预注册所有指标，确保 Prometheus 抓取时有初始值 */
    @PostConstruct
    public void initMetrics() {
        // 对话指标（用 unknown 占位，真实 agentId 首次请求后覆盖）
        List.of("unknown").forEach(agentId -> {
            Counter.builder("hify_chat_requests_total")
                    .tag("agent_id", agentId).description("Total chat requests")
                    .register(registry);
            DistributionSummary.builder("hify_chat_duration_ms")
                    .tag("agent_id", agentId).description("Chat request duration in milliseconds")
                    .publishPercentileHistogram(true).register(registry);
        });

        // LLM 指标
        List.of("OPENAI", "ANTHROPIC", "OLLAMA").forEach(provider -> {
            List.of("gpt-4o", "claude-3-5-sonnet", "llama3").forEach(model -> {
                List.of("true", "false").forEach(success -> {
                    Counter.builder("hify_llm_calls_total")
                            .tag("provider", provider).tag("model", model).tag("success", success)
                            .description("Total LLM API calls").register(registry);
                });
                DistributionSummary.builder("hify_llm_duration_ms")
                        .tag("provider", provider).tag("model", model)
                        .description("LLM API call duration in milliseconds")
                        .publishPercentileHistogram(true).register(registry);
            });
        });

        // 熔断器状态（各 provider 初始为 CLOSED=0）
        List.of("OPENAI", "ANTHROPIC", "OLLAMA").forEach(p -> circuitBreakerState(p, 0));

        // MCP 工具指标
        List.of("true", "false").forEach(success -> {
            Counter.builder("hify_mcp_tool_calls_total")
                    .tag("tool", "unknown").tag("success", success)
                    .description("Total MCP tool call attempts").register(registry);
        });
        DistributionSummary.builder("hify_mcp_tool_duration_ms")
                .tag("tool", "unknown").description("MCP tool call duration in milliseconds")
                .publishPercentileHistogram(true).register(registry);
    }

    // ── 对话 ──────────────────────────────────────────────────

    /** 对话请求计数，按 agentId 分组 */
    public void chatRequestIncrement(String agentId) {
        Counter.builder("hify_chat_requests_total")
                .tag("agent_id", agentId)
                .description("Total chat requests")
                .register(registry)
                .increment();
    }

    /** 对话请求耗时（毫秒），按 agentId 分组 */
    public void chatRequestDuration(String agentId, long durationMs) {
        DistributionSummary.builder("hify_chat_duration_ms")
                .tag("agent_id", agentId)
                .description("Chat request duration in milliseconds")
                .publishPercentileHistogram(true)   // 生成 _bucket，支持 histogram_quantile()
                .register(registry)
                .record(durationMs);
    }

    // ── LLM 调用 ──────────────────────────────────────────────

    /** LLM 调用计数，按 provider、model、success 分组 */
    public void llmCallIncrement(String provider, String model, boolean success) {
        Counter.builder("hify_llm_calls_total")
                .tag("provider", provider)
                .tag("model", model)
                .tag("success", String.valueOf(success))
                .description("Total LLM API calls")
                .register(registry)
                .increment();
    }

    /** LLM 调用耗时（毫秒），按 provider、model 分组 */
    public void llmCallDuration(String provider, String model, long durationMs) {
        DistributionSummary.builder("hify_llm_duration_ms")
                .tag("provider", provider)
                .tag("model", model)
                .description("LLM API call duration in milliseconds")
                .publishPercentileHistogram(true)
                .register(registry)
                .record(durationMs);
    }

    // ── 熔断器 ────────────────────────────────────────────────

    /**
     * 注册或更新熔断器状态 Gauge。
     * 状态编码：0=CLOSED, 1=OPEN, 2=HALF_OPEN
     */
    public void circuitBreakerState(String providerName, int stateCode) {
        circuitBreakerStates.put(providerName, stateCode);
        circuitBreakerGauges.computeIfAbsent(providerName, name ->
                Gauge.builder("hify_circuit_breaker_state",
                              circuitBreakerStates,
                              m -> m.getOrDefault(name, 0))
                     .tag("provider", name)
                     .description("Circuit breaker state: 0=CLOSED, 1=OPEN, 2=HALF_OPEN")
                     .register(registry)
        );
    }

    // ── MCP 工具调用 ──────────────────────────────────────────

    /** MCP 工具调用计数，按 tool、success 分组 */
    public void mcpToolCallIncrement(String toolName, boolean success) {
        Counter.builder("hify_mcp_tool_calls_total")
                .tag("tool", toolName)
                .tag("success", String.valueOf(success))
                .description("Total MCP tool call attempts")
                .register(registry)
                .increment();
    }

    /** MCP 工具调用耗时（毫秒） */
    public void mcpToolCallDuration(String toolName, long durationMs) {
        DistributionSummary.builder("hify_mcp_tool_duration_ms")
                .tag("tool", toolName)
                .description("MCP tool call duration in milliseconds")
                .publishPercentileHistogram(true)
                .register(registry)
                .record(durationMs);
    }
}
