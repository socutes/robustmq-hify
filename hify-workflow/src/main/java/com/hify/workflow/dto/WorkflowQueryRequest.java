package com.hify.workflow.dto;

import lombok.Data;

@Data
public class WorkflowQueryRequest {
    private int page = 1;
    private int pageSize = 20;
    private String status;
    private String name;
}
