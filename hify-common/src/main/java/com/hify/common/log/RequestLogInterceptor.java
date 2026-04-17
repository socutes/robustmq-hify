package com.hify.common.log;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.TraceFlags;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Slf4j
public class RequestLogInterceptor implements HandlerInterceptor {

    public static final String MDC_TRACE_ID  = "traceId";
    public static final String MDC_SESSION_ID = "sessionId";
    public static final String MDC_AGENT_ID   = "agentId";

    private static final String ATTR_START_TIME = "reqStartTime";
    private static final long   SLOW_THRESHOLD_MS = 1000;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        MDC.put(MDC_TRACE_ID, resolveTraceId());
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        long elapsed = System.currentTimeMillis() - (Long) request.getAttribute(ATTR_START_TIME);
        String method = request.getMethod();
        String path   = request.getRequestURI();
        int    status = response.getStatus();

        if (elapsed >= SLOW_THRESHOLD_MS) {
            log.warn("action=http_slow method={} path={} status={} durationMs={}", method, path, status, elapsed);
        } else {
            log.info("action=http_request method={} path={} status={} durationMs={}", method, path, status, elapsed);
        }

        MDC.clear();
    }

    /**
     * 优先使用 OTel 当前 Span 的 traceId（接入 OTel SDK 后自动生效）；
     * 无 Span 时 fallback 到随机 16 位 hex，保持格式一致。
     */
    private String resolveTraceId() {
        Span span = Span.current();
        if (span != null && span.getSpanContext().isValid()) {
            return span.getSpanContext().getTraceId();
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
