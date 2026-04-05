package com.hify.workflow.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkflowNodeReq {
    @NotBlank
    private String nodeKey;
    @NotBlank
    private String type;
    private String name;
    /** 节点配置，任意 JSON 对象 */
    private JsonNode config;
}
