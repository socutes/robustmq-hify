package com.hify.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.provider.dto.ProviderCreateRequest;
import com.hify.provider.dto.ProviderDetailResponse;
import com.hify.provider.dto.ProviderQueryRequest;
import com.hify.provider.dto.ProviderUpdateRequest;
import com.hify.provider.entity.ModelConfig;
import com.hify.provider.entity.Provider;
import com.hify.provider.service.ProviderService;
import com.hify.provider.entity.ProviderHealth;
import com.hify.provider.mapper.ModelConfigMapper;
import com.hify.provider.mapper.ProviderHealthMapper;
import com.hify.provider.mapper.ProviderMapper;
import com.hify.provider.service.ProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProviderServiceImpl implements ProviderService {

    private final ProviderMapper providerMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final ProviderHealthMapper providerHealthMapper;

    @Override
    @CacheEvict(cacheNames = "provider-cache", allEntries = true)
    public Provider create(ProviderCreateRequest request) {
        checkNameUnique(request.getName(), null);

        Provider provider = new Provider();
        provider.setName(request.getName());
        provider.setType(request.getType());
        provider.setBaseUrl(request.getBaseUrl());
        provider.setAuthConfig(request.getAuthConfig());
        provider.setDescription(request.getDescription() != null ? request.getDescription() : "");
        provider.setEnabled(1);
        providerMapper.insert(provider);
        return provider;
    }

    @Override
    @Caching(evict = {
        @CacheEvict(cacheNames = "provider-cache", key = "#id"),
        @CacheEvict(cacheNames = "provider-cache", key = "'list'")
    })
    public Provider update(Long id, ProviderUpdateRequest request) {
        Provider provider = getOrThrow(id);
        checkNameUnique(request.getName(), id);

        provider.setName(request.getName());
        provider.setBaseUrl(request.getBaseUrl());
        provider.setAuthConfig(request.getAuthConfig());
        if (request.getDescription() != null) {
            provider.setDescription(request.getDescription());
        }
        providerMapper.updateById(provider);
        return provider;
    }

    @Override
    @Caching(evict = {
        @CacheEvict(cacheNames = "provider-cache", key = "#id"),
        @CacheEvict(cacheNames = "provider-cache", key = "'list'")
    })
    public void delete(Long id) {
        getOrThrow(id);
        providerMapper.deleteById(id);
    }

    @Override
    @Caching(evict = {
        @CacheEvict(cacheNames = "provider-cache", key = "#id"),
        @CacheEvict(cacheNames = "provider-cache", key = "'list'")
    })
    public void toggleEnabled(Long id) {
        Provider provider = getOrThrow(id);
        provider.setEnabled(provider.getEnabled() == 1 ? 0 : 1);
        providerMapper.updateById(provider);
    }

    @Override
    @Cacheable(cacheNames = "provider-cache", key = "#id")
    public ProviderDetailResponse getDetail(Long id) {
        Provider provider = getOrThrow(id);

        List<ModelConfig> models = modelConfigMapper.selectList(
            new LambdaQueryWrapper<ModelConfig>()
                .eq(ModelConfig::getProviderId, id)
                .eq(ModelConfig::getEnabled, 1)
                .orderByAsc(ModelConfig::getCreatedAt)
        );

        ProviderHealth health = providerHealthMapper.findByProviderId(id).orElse(null);

        return ProviderDetailResponse.from(provider, models, health);
    }

    @Override
    @Cacheable(cacheNames = "provider-cache", key = "'list'")
    public Result<PageResult<ProviderDetailResponse>> list(ProviderQueryRequest request) {
        LambdaQueryWrapper<Provider> wrapper = new LambdaQueryWrapper<Provider>()
            .eq(StringUtils.hasText(request.getType()), Provider::getType, request.getType())
            .eq(request.getEnabled() != null, Provider::getEnabled, request.getEnabled())
            .orderByDesc(Provider::getCreatedAt);

        int pageSize = Math.min(request.getPageSize(), 100);
        var page = providerMapper.selectPage(new Page<>(request.getPage(), pageSize), wrapper);

        List<ProviderDetailResponse> items = page.getRecords().stream().map(provider -> {
            List<ModelConfig> models = modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfig>()
                    .eq(ModelConfig::getProviderId, provider.getId())
                    .orderByAsc(ModelConfig::getCreatedAt)
            );
            ProviderHealth health = providerHealthMapper.findByProviderId(provider.getId()).orElse(null);
            return ProviderDetailResponse.from(provider, models, health);
        }).collect(Collectors.toList());

        return PageResult.of(items, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public ModelConfig getEnabledModelConfigOrThrow(Long modelConfigId) {
        ModelConfig mc = modelConfigMapper.selectById(modelConfigId);
        if (mc == null) {
            throw new BizException(ErrorCode.MODEL_CONFIG_NOT_FOUND);
        }
        if (mc.getEnabled() != 1) {
            throw new BizException(ErrorCode.MODEL_CONFIG_DISABLED);
        }
        return mc;
    }

    // ── 内部工具 ───────────────────────────────────────────────

    private Provider getOrThrow(Long id) {
        Provider provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BizException(ErrorCode.PROVIDER_NOT_FOUND);
        }
        return provider;
    }

    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<Provider> wrapper = new LambdaQueryWrapper<Provider>()
            .eq(Provider::getName, name)
            .ne(excludeId != null, Provider::getId, excludeId);
        if (providerMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PROVIDER_NAME_DUPLICATE);
        }
    }
}
