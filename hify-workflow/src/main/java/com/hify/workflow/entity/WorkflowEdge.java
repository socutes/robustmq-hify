package com.hify.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("workflow_edge")
public class WorkflowEdge extends BaseEntity {
    private Long workflowId;
    private String sourceNodeKey;
    private String targetNodeKey;
    /** 条件表达式，NULL 表示无条件直接走 */
    private String conditionExpr;
}
