package com.hify.common.log;

import org.slf4j.MDC;

import java.util.Map;

/**
 * 把父线程的 MDC 上下文（traceId、sessionId 等）传播到子线程。
 * 用法：executor.execute(MdcTaskWrapper.wrap(() -> doWork()));
 */
public final class MdcTaskWrapper {

    private MdcTaskWrapper() {}

    public static Runnable wrap(Runnable task) {
        Map<String, String> parentMdc = MDC.getCopyOfContextMap();
        return () -> {
            if (parentMdc != null) {
                MDC.setContextMap(parentMdc);
            }
            try {
                task.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
