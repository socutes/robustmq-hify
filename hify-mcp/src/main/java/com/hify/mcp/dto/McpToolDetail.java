package com.hify.mcp.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class McpToolDetail {

    private String name;
    private String description;
    /** JSON Schema of inputSchema, e.g. {type:"object", properties:{...}, required:[...]} */
    private Map<String, Object> inputSchema;
    /** Derived from inputSchema.required for convenience */
    private List<String> requiredParams;
}
