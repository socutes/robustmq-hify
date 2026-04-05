package com.hify.workflow.dto;

import com.hify.workflow.entity.Workflow;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowListItem {
    private Long id;
    private String name;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WorkflowListItem from(Workflow wf) {
        WorkflowListItem item = new WorkflowListItem();
        item.setId(wf.getId());
        item.setName(wf.getName());
        item.setDescription(wf.getDescription());
        item.setStatus(wf.getStatus());
        item.setCreatedAt(wf.getCreatedAt());
        item.setUpdatedAt(wf.getUpdatedAt());
        return item;
    }
}
