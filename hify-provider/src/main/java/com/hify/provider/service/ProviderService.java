package com.hify.provider.service;

import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.provider.dto.ProviderCreateRequest;
import com.hify.provider.dto.ProviderDetailResponse;
import com.hify.provider.dto.ProviderQueryRequest;
import com.hify.provider.dto.ProviderUpdateRequest;
import com.hify.provider.entity.Provider;

public interface ProviderService {

    Provider create(ProviderCreateRequest request);

    Provider update(Long id, ProviderUpdateRequest request);

    void delete(Long id);

    void toggleEnabled(Long id);

    ProviderDetailResponse getDetail(Long id);

    Result<PageResult<ProviderDetailResponse>> list(ProviderQueryRequest request);
}
