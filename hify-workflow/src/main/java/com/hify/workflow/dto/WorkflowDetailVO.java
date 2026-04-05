package com.hify.workflow.dto;

import com.hify.workflow.entity.Workflow;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WorkflowDetailVO {
    private Long id;
    private String name;
    private String description;
    private String status;
    private List<WorkflowNodeVO> nodes;
    private List<WorkflowEdgeVO> edges;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WorkflowDetailVO from(Workflow wf, List<WorkflowNodeVO> nodes, List<WorkflowEdgeVO> edges) {
        WorkflowDetailVO vo = new WorkflowDetailVO();
        vo.setId(wf.getId());
        vo.setName(wf.getName());
        vo.setDescription(wf.getDescription());
        vo.setStatus(wf.getStatus());
        vo.setNodes(nodes);
        vo.setEdges(edges);
        vo.setCreatedAt(wf.getCreatedAt());
        vo.setUpdatedAt(wf.getUpdatedAt());
        return vo;
    }
}
