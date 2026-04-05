package com.hify.workflow.dto;

import com.hify.workflow.entity.WorkflowEdge;
import lombok.Data;

@Data
public class WorkflowEdgeVO {
    private Long id;
    private String sourceNodeKey;
    private String targetNodeKey;
    private String condition;

    public static WorkflowEdgeVO from(WorkflowEdge edge) {
        WorkflowEdgeVO vo = new WorkflowEdgeVO();
        vo.setId(edge.getId());
        vo.setSourceNodeKey(edge.getSourceNodeKey());
        vo.setTargetNodeKey(edge.getTargetNodeKey());
        vo.setCondition(edge.getConditionExpr());
        return vo;
    }
}
