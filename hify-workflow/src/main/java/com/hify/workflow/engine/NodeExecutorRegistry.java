package com.hify.workflow.engine;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NodeExecutor 分发注册表。
 * Spring 自动注入所有 NodeExecutor 实现，按 nodeType() 建索引。
 */
@Slf4j
@Component
public class NodeExecutorRegistry {

    private final Map<String, NodeExecutor> executorMap;

    public NodeExecutorRegistry(List<NodeExecutor> executors) {
        executorMap = new HashMap<>();
        for (NodeExecutor executor : executors) {
            executorMap.put(executor.nodeType().toUpperCase(), executor);
        }
        log.info("NodeExecutorRegistry registered types: {}", executorMap.keySet());
    }

    public NodeExecutor get(String type) {
        NodeExecutor executor = executorMap.get(type.toUpperCase());
        if (executor == null) {
            throw new BizException(ErrorCode.WORKFLOW_EXECUTE_FAILED,
                    "未找到节点执行器: " + type);
        }
        return executor;
    }
}
