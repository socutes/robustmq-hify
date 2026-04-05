package com.hify.workflow.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 贯穿整个工作流执行的变量池。
 * 对标 Dify 的 VariablePool：节点只写自己 nodeKey 下的变量，
 * 读可以跨节点，key 格式 = nodeKey.varName。
 */
public class ExecutionContext {

    private final Long workflowRunId;
    private final Map<String, Object> variables = new LinkedHashMap<>();

    public ExecutionContext(Long workflowRunId, String userMessage) {
        this.workflowRunId = workflowRunId;
        // 预写入用户消息，所有节点默认可读 {{start.userMessage}}
        variables.put("start.userMessage", userMessage != null ? userMessage : "");
    }

    public Long getWorkflowRunId() {
        return workflowRunId;
    }

    /** 写入变量。key = nodeKey.varName，只增不覆盖历史。 */
    public void set(String nodeKey, String varName, Object value) {
        variables.put(nodeKey + "." + varName, value != null ? value : "");
    }

    /** 读取变量，不存在时返回 null。 */
    public Object get(String nodeKey, String varName) {
        return variables.get(nodeKey + "." + varName);
    }

    /**
     * 替换模板变量：把 {{nodeKey.varName}} 替换为实际值。
     * 变量不存在时保留原始占位符，不报错。
     */
    public String resolve(String template) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue().toString());
        }
        return result;
    }

    /** 返回所有变量的只读快照，用于执行记录落库。 */
    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }
}
