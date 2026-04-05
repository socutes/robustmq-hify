package com.hify.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.dto.NodeConfigDef;
import com.hify.workflow.dto.NodeConfigParser;
import com.hify.workflow.entity.WorkflowEdge;
import com.hify.workflow.entity.WorkflowNode;
import com.hify.workflow.entity.WorkflowNodeRun;
import com.hify.workflow.entity.WorkflowRun;
import com.hify.workflow.mapper.WorkflowEdgeMapper;
import com.hify.workflow.mapper.WorkflowNodeMapper;
import com.hify.workflow.mapper.WorkflowNodeRunMapper;
import com.hify.workflow.mapper.WorkflowRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作流执行引擎。
 * 从 START 节点开始，按边一步一步执行到 END 节点。
 * 同步执行，不引入新的异步机制（调用方已在 llmExecutor 线程里）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    private static final int MAX_STEPS = 50; // 防止死循环

    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowEdgeMapper edgeMapper;
    private final WorkflowRunMapper runMapper;
    private final WorkflowNodeRunMapper nodeRunMapper;
    private final NodeConfigParser configParser;
    private final NodeExecutorRegistry executorRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 执行工作流，返回最终输出字符串。
     */
    public String execute(Long workflowId, String userMessage) {
        long globalStart = System.currentTimeMillis();

        // 1. 加载配置，构建内存结构
        Map<String, WorkflowNode> nodeMap = loadNodeMap(workflowId);
        Map<String, List<WorkflowEdge>> edgeMap = loadEdgeMap(workflowId);

        if (nodeMap.isEmpty()) {
            throw new BizException(ErrorCode.WORKFLOW_EXECUTE_FAILED, "工作流没有节点");
        }

        // 2. 创建执行记录
        WorkflowRun run = createRun(workflowId, userMessage);
        ExecutionContext ctx = new ExecutionContext(run.getId(), userMessage);

        try {
            // 3. 找 START 节点
            String currentKey = findStartKey(nodeMap);
            int stepCount = 0;

            // 4. 主循环：逐节点执行
            while (currentKey != null) {
                if (++stepCount > MAX_STEPS) {
                    throw new BizException(ErrorCode.WORKFLOW_EXECUTE_FAILED,
                            "执行步数超过 " + MAX_STEPS + " 步，可能存在死循环");
                }

                WorkflowNode node = nodeMap.get(currentKey);
                if (node == null) {
                    throw new BizException(ErrorCode.WORKFLOW_EXECUTE_FAILED,
                            "节点不存在: " + currentKey);
                }

                // END 节点：结束循环
                if ("END".equals(node.getType())) {
                    break;
                }

                // START 节点：直接跳到下一个
                if ("START".equals(node.getType())) {
                    currentKey = pickNext(edgeMap, currentKey, node, ctx);
                    continue;
                }

                // 执行普通节点
                currentKey = executeNode(node, edgeMap, ctx, currentKey);
            }

            // 5. 取最终输出：从 END 节点的 outputVariable 找
            String output = resolveOutput(nodeMap, ctx, currentKey);

            int elapsed = (int) (System.currentTimeMillis() - globalStart);
            finishRun(run, "SUCCESS", output, null, elapsed);
            log.info("WorkflowEngine finished workflowId={} runId={} steps={} elapsed={}ms",
                    workflowId, run.getId(), stepCount, elapsed);
            return output;

        } catch (Exception e) {
            int elapsed = (int) (System.currentTimeMillis() - globalStart);
            finishRun(run, "FAILED", null, e.getMessage(), elapsed);
            log.error("WorkflowEngine failed workflowId={} runId={}: {}", workflowId, run.getId(), e.getMessage());
            if (e instanceof BizException) throw (BizException) e;
            throw new BizException(ErrorCode.WORKFLOW_EXECUTE_FAILED, e.getMessage());
        }
    }

    /** 执行单个节点，返回下一个节点 key。 */
    private String executeNode(WorkflowNode node, Map<String, List<WorkflowEdge>> edgeMap,
                                ExecutionContext ctx, String currentKey) {
        WorkflowNodeRun nodeRun = createNodeRun(ctx.getWorkflowRunId(), node);
        long nodeStart = System.currentTimeMillis();

        try {
            NodeConfigDef config = configParser.parse(node.getType(), node.getConfig());
            executorRegistry.get(node.getType()).execute(node, config, ctx);

            int elapsed = (int) (System.currentTimeMillis() - nodeStart);
            finishNodeRun(nodeRun, "SUCCESS", ctx, null, elapsed);
            return pickNext(edgeMap, currentKey, node, ctx);

        } catch (Exception e) {
            int elapsed = (int) (System.currentTimeMillis() - nodeStart);
            finishNodeRun(nodeRun, "FAILED", ctx, e.getMessage(), elapsed);
            throw e; // 向外抛，WorkflowRun 统一更新为 FAILED
        }
    }

    /**
     * 根据当前节点类型和 ctx 选择下一个节点 key。
     *
     * CONDITION 节点：从 ctx 取结果值，匹配边的 conditionExpr（字符串 == 比较）
     * 其他节点：先找无条件边（conditionExpr == null），没有则取第一条
     * 所有边都不匹配时返回 null（流程结束）
     */
    private String pickNext(Map<String, List<WorkflowEdge>> edgeMap,
                             String currentKey, WorkflowNode node, ExecutionContext ctx) {
        List<WorkflowEdge> outEdges = edgeMap.getOrDefault(currentKey, List.of());
        if (outEdges.isEmpty()) return null;

        if ("CONDITION".equals(node.getType())) {
            // 取 CONDITION 节点写入 ctx 的结果值
            NodeConfigDef.ConditionConfig cond = null;
            try {
                cond = (NodeConfigDef.ConditionConfig) configParser.parse(node.getType(), node.getConfig());
            } catch (Exception ignored) {}

            String outputVar = cond != null && cond.outputVariable() != null ? cond.outputVariable() : "result";
            Object condResult = ctx.get(node.getNodeKey(), outputVar);
            String condStr = condResult != null ? condResult.toString().trim() : "";

            // 找 conditionExpr 和结果匹配的边
            for (WorkflowEdge edge : outEdges) {
                if (condStr.equalsIgnoreCase(edge.getConditionExpr())) {
                    return edge.getTargetNodeKey();
                }
            }
            // 没有精确匹配，找 null 默认边作为 fallback
            for (WorkflowEdge edge : outEdges) {
                if (edge.getConditionExpr() == null || edge.getConditionExpr().isBlank()) {
                    return edge.getTargetNodeKey();
                }
            }
            log.warn("CONDITION 节点 {} 所有分支都不匹配，condResult='{}', 流程结束", node.getNodeKey(), condStr);
            return null;
        }

        // 非 CONDITION 节点：优先无条件边
        for (WorkflowEdge edge : outEdges) {
            if (edge.getConditionExpr() == null || edge.getConditionExpr().isBlank()) {
                return edge.getTargetNodeKey();
            }
        }
        // 没有无条件边，取第一条
        return outEdges.get(0).getTargetNodeKey();
    }

    private String findStartKey(Map<String, WorkflowNode> nodeMap) {
        return nodeMap.values().stream()
                .filter(n -> "START".equals(n.getType()))
                .map(WorkflowNode::getNodeKey)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.WORKFLOW_EXECUTE_FAILED, "工作流缺少 START 节点"));
    }

    private String resolveOutput(Map<String, WorkflowNode> nodeMap, ExecutionContext ctx, String endKey) {
        if (endKey != null) {
            WorkflowNode endNode = nodeMap.get(endKey);
            if (endNode != null && "END".equals(endNode.getType())) {
                try {
                    NodeConfigDef.EndConfig endConfig =
                            (NodeConfigDef.EndConfig) configParser.parse("END", endNode.getConfig());
                    if (endConfig.outputVariable() != null) {
                        // 遍历所有节点找这个变量
                        Map<String, Object> snapshot = ctx.snapshot();
                        for (Map.Entry<String, Object> e : snapshot.entrySet()) {
                            if (e.getKey().endsWith("." + endConfig.outputVariable())) {
                                return e.getValue().toString();
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        // fallback：取最后写入的变量值
        Map<String, Object> snapshot = ctx.snapshot();
        Object last = null;
        for (Object v : snapshot.values()) last = v;
        return last != null ? last.toString() : "";
    }

    // ── 执行记录 ──────────────────────────────────────────────────

    private WorkflowRun createRun(Long workflowId, String input) {
        WorkflowRun run = new WorkflowRun();
        run.setWorkflowId(workflowId);
        run.setStatus("RUNNING");
        run.setInput(input);
        try {
            runMapper.insert(run);
        } catch (Exception e) {
            log.warn("WorkflowRun 落库失败: {}", e.getMessage());
        }
        return run;
    }

    private void finishRun(WorkflowRun run, String status, String output, String error, int elapsed) {
        run.setStatus(status);
        run.setOutput(output);
        run.setError(error != null && error.length() > 500 ? error.substring(0, 500) : error);
        run.setElapsedMs(elapsed);
        run.setFinishedAt(LocalDateTime.now());
        try {
            runMapper.updateById(run);
        } catch (Exception e) {
            log.warn("WorkflowRun 更新失败: {}", e.getMessage());
        }
    }

    private WorkflowNodeRun createNodeRun(Long runId, WorkflowNode node) {
        WorkflowNodeRun nodeRun = new WorkflowNodeRun();
        nodeRun.setWorkflowRunId(runId);
        nodeRun.setNodeKey(node.getNodeKey());
        nodeRun.setNodeType(node.getType());
        nodeRun.setStatus("RUNNING");
        try {
            nodeRunMapper.insert(nodeRun);
        } catch (Exception e) {
            log.warn("WorkflowNodeRun 落库失败: {}", e.getMessage());
        }
        return nodeRun;
    }

    private void finishNodeRun(WorkflowNodeRun nodeRun, String status,
                                ExecutionContext ctx, String error, int elapsed) {
        nodeRun.setStatus(status);
        nodeRun.setElapsedMs(elapsed);
        nodeRun.setFinishedAt(LocalDateTime.now());
        if (error != null) {
            nodeRun.setError(error.length() > 500 ? error.substring(0, 500) : error);
        }
        try {
            nodeRun.setOutputs(objectMapper.writeValueAsString(ctx.snapshot()));
        } catch (Exception ignored) {}
        try {
            nodeRunMapper.updateById(nodeRun);
        } catch (Exception e) {
            log.warn("WorkflowNodeRun 更新失败: {}", e.getMessage());
        }
    }

    // ── 加载配置 ──────────────────────────────────────────────────

    private Map<String, WorkflowNode> loadNodeMap(Long workflowId) {
        return nodeMapper.selectList(
                new LambdaQueryWrapper<WorkflowNode>()
                        .eq(WorkflowNode::getWorkflowId, workflowId)
                        .orderByAsc(WorkflowNode::getId)
        ).stream().collect(Collectors.toMap(WorkflowNode::getNodeKey, n -> n));
    }

    private Map<String, List<WorkflowEdge>> loadEdgeMap(Long workflowId) {
        List<WorkflowEdge> edges = edgeMapper.selectList(
                new LambdaQueryWrapper<WorkflowEdge>()
                        .eq(WorkflowEdge::getWorkflowId, workflowId)
        );
        Map<String, List<WorkflowEdge>> map = new HashMap<>();
        for (WorkflowEdge edge : edges) {
            map.computeIfAbsent(edge.getSourceNodeKey(), k -> new ArrayList<>()).add(edge);
        }
        return map;
    }
}
