package com.hify.common.log;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Slf4j
public class RequestLogInterceptor implements HandlerInterceptor {

    private static final String ATTR_START_TIME = "reqStartTime";
    private static final String MDC_TRACE_ID    = "traceId";
    private static final long   SLOW_THRESHOLD_MS = 1000;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        MDC.put(MDC_TRACE_ID, generateTraceId());
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        long start = (Long) request.getAttribute(ATTR_START_TIME);
        long elapsed = System.currentTimeMillis() - start;
        int status = response.getStatus();
        String method = request.getMethod();
        String path = request.getRequestURI();

        if (elapsed >= SLOW_THRESHOLD_MS) {
            log.warn("SLOW {} {} {} {}ms", method, path, status, elapsed);
        } else {
            log.info("{} {} {} {}ms", method, path, status, elapsed);
        }

        MDC.remove(MDC_TRACE_ID);
    }

    private String generateTraceId() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return uuid.substring(0, 16);
    }
}
