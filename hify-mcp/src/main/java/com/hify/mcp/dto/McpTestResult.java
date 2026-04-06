package com.hify.mcp.dto;

import lombok.Data;

import java.util.List;

@Data
public class McpTestResult {

    private boolean success;
    private int latencyMs;
    private List<String> tools;
    private String errorMessage;

    public static McpTestResult ok(int latencyMs, List<String> tools) {
        McpTestResult r = new McpTestResult();
        r.success = true;
        r.latencyMs = latencyMs;
        r.tools = tools;
        return r;
    }

    public static McpTestResult fail(String errorMessage) {
        McpTestResult r = new McpTestResult();
        r.success = false;
        r.errorMessage = errorMessage;
        return r;
    }
}
