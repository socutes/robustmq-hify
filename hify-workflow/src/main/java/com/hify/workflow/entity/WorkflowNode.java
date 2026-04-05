package com.hify.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("workflow_node")
public class WorkflowNode extends BaseEntity {
    private Long workflowId;
    /** 工作流内唯一标识，如 "classify"、"router" */
    private String nodeKey;
    /** LLM / CONDITION / API_CALL / KNOWLEDGE / START / END */
    private String type;
    private String name;
    /** JSON 格式的节点配置 */
    private String config;
}
