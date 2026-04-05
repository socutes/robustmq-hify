package com.hify.workflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class WorkflowCreateRequest {
    @NotBlank(message = "名称不能为空")
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    @Valid
    private List<WorkflowNodeReq> nodes;

    @Valid
    private List<WorkflowEdgeReq> edges;
}
