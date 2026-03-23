package com.hify.provider.service;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.provider.adapter.ProviderAdapterFactory;
import com.hify.provider.dto.ConnectionTestResult;
import com.hify.provider.entity.Provider;
import com.hify.provider.mapper.ProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderConnectionTestService {

    private final ProviderAdapterFactory adapterFactory;
    private final ProviderMapper providerMapper;

    /** 连通性测试专用 10s 超时客户端 */
    private final OkHttpClient testClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public ConnectionTestResult testById(Long id) {
        Provider provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BizException(ErrorCode.PROVIDER_NOT_FOUND);
        }
        return test(provider);
    }

    public ConnectionTestResult test(Provider provider) {
        log.info("连通性测试 provider={} type={}", provider.getName(), provider.getType());
        return adapterFactory.get(provider.getType()).testConnection(provider, testClient);
    }
}
