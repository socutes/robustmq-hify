package com.hify.mcp.dto;

import lombok.Data;

@Data
public class McpDebugResult {

    private String result;
    private int elapsedMs;
    private boolean success;
    private String errorMessage;

    public static McpDebugResult ok(String result, int elapsedMs) {
        McpDebugResult r = new McpDebugResult();
        r.success = true;
        r.result = result;
        r.elapsedMs = elapsedMs;
        return r;
    }

    public static McpDebugResult fail(String errorMessage, int elapsedMs) {
        McpDebugResult r = new McpDebugResult();
        r.success = false;
        r.errorMessage = errorMessage;
        r.elapsedMs = elapsedMs;
        return r;
    }
}
