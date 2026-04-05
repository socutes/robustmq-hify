package com.hify.workflow.engine.executor;

import com.hify.workflow.dto.NodeConfigDef;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.NodeExecutor;
import com.hify.workflow.entity.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 条件分支节点执行器。
 * 把 expression 替换变量后，把结果值写入 ctx。
 * 边的 conditionExpr 和这个结果做字符串匹配来决定走哪条路。
 */
@Slf4j
@Component
public class ConditionNodeExecutor implements NodeExecutor {

    @Override
    public String nodeType() {
        return "CONDITION";
    }

    @Override
    public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        NodeConfigDef.ConditionConfig conditionConfig = (NodeConfigDef.ConditionConfig) config;
        String expression = conditionConfig.expression() != null ? conditionConfig.expression() : "";
        String outputVar = conditionConfig.outputVariable() != null ? conditionConfig.outputVariable() : "result";

        // 替换变量，得到实际值（如 "售后"、"true"、"false"）
        String resolved = ctx.resolve(expression).trim();

        // 简单布尔判断支持
        if ("true".equalsIgnoreCase(resolved)) {
            ctx.set(node.getNodeKey(), outputVar, "true");
        } else if ("false".equalsIgnoreCase(resolved)) {
            ctx.set(node.getNodeKey(), outputVar, "false");
        } else {
            // 字面量值，直接传出（如 "售前"、"售后"、"技术支持"）
            ctx.set(node.getNodeKey(), outputVar, resolved);
        }

        log.info("ConditionNodeExecutor node={} expression='{}' → result='{}'",
                node.getNodeKey(), expression, resolved);
    }
}
