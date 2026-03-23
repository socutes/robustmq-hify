package com.hify.provider.service;

import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.provider.dto.ProviderCreateRequest;
import com.hify.provider.dto.ProviderDetailResponse;
import com.hify.provider.dto.ProviderQueryRequest;
import com.hify.provider.dto.ProviderUpdateRequest;
import com.hify.provider.entity.ModelConfig;
import com.hify.provider.entity.Provider;

public interface ProviderService {

    Provider create(ProviderCreateRequest request);

    Provider update(Long id, ProviderUpdateRequest request);

    void delete(Long id);

    void toggleEnabled(Long id);

    ProviderDetailResponse getDetail(Long id);

    Result<PageResult<ProviderDetailResponse>> list(ProviderQueryRequest request);

    /** 跨模块调用：校验 modelConfigId 存在且已启用 */
    ModelConfig getEnabledModelConfigOrThrow(Long modelConfigId);
}
