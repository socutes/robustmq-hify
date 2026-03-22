package com.hify.provider.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.provider.dto.ConnectionTestResult;
import com.hify.provider.entity.Provider;
import com.hify.provider.entity.ProviderHealth;
import com.hify.provider.mapper.ProviderHealthMapper;
import com.hify.provider.mapper.ProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hify.health-check.enabled", havingValue = "true", matchIfMissing = true)
public class ProviderHealthCheckTask {

    private static final int FAIL_THRESHOLD = 3;

    private final ProviderMapper providerMapper;
    private final ProviderHealthMapper providerHealthMapper;
    private final ProviderConnectionTestService connectionTestService;

    @Qualifier("asyncExecutor")
    private final Executor asyncExecutor;

    @Scheduled(fixedDelay = 60_000)
    public void checkAll() {
        List<Provider> providers = providerMapper.selectList(
                new LambdaQueryWrapper<Provider>().eq(Provider::getEnabled, 1)
        );

        if (providers.isEmpty()) return;

        log.debug("开始健康检查，共 {} 个供应商", providers.size());

        List<CompletableFuture<Void>> futures = providers.stream()
                .map(provider -> CompletableFuture.runAsync(
                        () -> checkOne(provider), asyncExecutor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((v, ex) -> log.debug("健康检查完成，共 {} 个供应商", providers.size()));
    }

    private void checkOne(Provider provider) {
        ConnectionTestResult result = connectionTestService.test(provider);
        ProviderHealth health = providerHealthMapper.findByProviderId(provider.getId())
                .orElseGet(() -> newHealth(provider.getId()));

        health.setLastCheckAt(LocalDateTime.now());

        if (result.isSuccess()) {
            health.setStatus("UP");
            health.setLatencyMs(result.getLatencyMs());
            health.setLastSuccessAt(LocalDateTime.now());
            health.setFailCount(0);
            health.setErrorMessage(null);
        } else {
            int failCount = health.getFailCount() + 1;
            health.setFailCount(failCount);
            health.setErrorMessage(result.getErrorMessage());
            health.setStatus(failCount >= FAIL_THRESHOLD ? "DOWN" : "DEGRADED");
            log.warn("供应商 [{}] 健康检查失败 failCount={} status={}: {}",
                    provider.getName(), failCount, health.getStatus(), result.getErrorMessage());
        }

        if (health.getId() == null) {
            providerHealthMapper.insert(health);
        } else {
            providerHealthMapper.updateById(health);
        }
    }

    private ProviderHealth newHealth(Long providerId) {
        ProviderHealth health = new ProviderHealth();
        health.setProviderId(providerId);
        health.setStatus("UNKNOWN");
        health.setFailCount(0);
        return health;
    }
}
