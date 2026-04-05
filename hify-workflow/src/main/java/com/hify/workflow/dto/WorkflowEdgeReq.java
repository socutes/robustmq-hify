package com.hify.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkflowEdgeReq {
    @NotBlank
    private String sourceNodeKey;
    @NotBlank
    private String targetNodeKey;
    /** 条件表达式，null 表示无条件直接走 */
    private String condition;
}
