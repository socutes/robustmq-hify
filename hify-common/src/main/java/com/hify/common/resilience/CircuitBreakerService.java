package com.hify.common.resilience;

import com.hify.common.http.LlmApiException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CircuitBreakerService {

    private static final CircuitBreakerConfig CB_CONFIG = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            // AUTH_FAILED 属于业务错误不计入熔断失败率，TIMEOUT/RATE_LIMITED/UNKNOWN 计入
            .recordException(e -> {
                if (e instanceof LlmApiException ex) {
                    return ex.getType() != LlmApiException.Type.AUTH_FAILED;
                }
                return true;
            })
            .build();

    // 网络超时重试：固定间隔 1s，最多 2 次重试
    private static final RetryConfig TIMEOUT_RETRY_CONFIG = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(1))
            .retryOnException(e -> {
                if (e instanceof LlmApiException ex) {
                    return ex.getType() == LlmApiException.Type.TIMEOUT;
                }
                return e instanceof SocketTimeoutException;
            })
            .build();

    // 限流退避重试：指数退避 2s → 4s，最多 2 次重试
    private static final RetryConfig RATE_LIMIT_RETRY_CONFIG = RetryConfig.custom()
            .maxAttempts(3)
            .intervalFunction(attempt -> Duration.ofSeconds((long) Math.pow(2, attempt)).toMillis())
            .retryOnException(e ->
                e instanceof LlmApiException ex &&
                ex.getType() == LlmApiException.Type.RATE_LIMITED
            )
            .build();

    private final ConcurrentHashMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Retry> timeoutRetries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Retry> rateLimitRetries = new ConcurrentHashMap<>();

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    public CircuitBreakerService(CircuitBreakerRegistry circuitBreakerRegistry,
                                 RetryRegistry retryRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
    }

    /**
     * 按 providerName 获取或创建独立熔断器。
     */
    public CircuitBreaker getCircuitBreaker(String providerName) {
        return circuitBreakers.computeIfAbsent(providerName, name -> {
            CircuitBreaker cb = CircuitBreaker.of(name, CB_CONFIG);
            cb.getEventPublisher()
                .onStateTransition(e -> log.warn(
                    "熔断器 [{}] 状态变更：{} → {}",
                    name, e.getStateTransition().getFromState(), e.getStateTransition().getToState()
                ));
            log.info("创建熔断器 [{}]", name);
            return cb;
        });
    }

    /**
     * 用熔断 + 重试包装调用。
     * 重试策略：超时重试（固定 1s）+ 限流退避（2s/4s），认证失败不重试直接抛出。
     */
    public <T> T execute(String providerName, Callable<T> callable) throws Exception {
        CircuitBreaker cb = getCircuitBreaker(providerName);
        Retry timeoutRetry = timeoutRetries.computeIfAbsent(
                providerName + "-timeout",
                name -> Retry.of(name, TIMEOUT_RETRY_CONFIG)
        );
        Retry rateLimitRetry = rateLimitRetries.computeIfAbsent(
                providerName + "-ratelimit",
                name -> Retry.of(name, RATE_LIMIT_RETRY_CONFIG)
        );

        // 认证失败直接抛出，不进入重试
        Callable<T> withRateLimitRetry = Retry.decorateCallable(rateLimitRetry, callable);
        Callable<T> withTimeoutRetry   = Retry.decorateCallable(timeoutRetry, withRateLimitRetry);
        Callable<T> withCircuitBreaker = CircuitBreaker.decorateCallable(cb, withTimeoutRetry);

        return withCircuitBreaker.call();
    }
}
