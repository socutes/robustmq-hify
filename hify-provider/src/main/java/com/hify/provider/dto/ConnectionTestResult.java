package com.hify.provider.dto;

import lombok.Data;

@Data
public class ConnectionTestResult {

    private boolean success;
    private int latencyMs;
    private int modelCount;
    private String errorMessage;

    public static ConnectionTestResult ok(int latencyMs, int modelCount) {
        ConnectionTestResult r = new ConnectionTestResult();
        r.success = true;
        r.latencyMs = latencyMs;
        r.modelCount = modelCount;
        return r;
    }

    public static ConnectionTestResult fail(String errorMessage) {
        ConnectionTestResult r = new ConnectionTestResult();
        r.success = false;
        r.errorMessage = errorMessage;
        return r;
    }
}
