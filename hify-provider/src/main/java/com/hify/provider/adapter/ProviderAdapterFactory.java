package com.hify.provider.adapter;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ProviderAdapterFactory {

    private final Map<String, ProviderAdapter> adapterMap;

    public ProviderAdapterFactory(List<ProviderAdapter> adapters) {
        adapterMap = new HashMap<>();
        for (ProviderAdapter adapter : adapters) {
            for (String type : adapter.supportedTypes()) {
                adapterMap.put(type.toUpperCase(), adapter);
            }
        }
        log.info("ProviderAdapterFactory registered types: {}", adapterMap.keySet());
    }

    public ProviderAdapter get(String type) {
        ProviderAdapter adapter = adapterMap.get(type.toUpperCase());
        if (adapter == null) {
            throw new BizException(ErrorCode.PROVIDER_TYPE_NOT_SUPPORTED);
        }
        return adapter;
    }
}
