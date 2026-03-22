package com.hify.provider.controller;

import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.provider.dto.*;
import com.hify.provider.entity.Provider;
import com.hify.provider.service.ProviderConnectionTestService;
import com.hify.provider.service.ProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;
    private final ProviderConnectionTestService connectionTestService;

    @PostMapping
    public Result<Provider> create(@Valid @RequestBody ProviderCreateRequest request) {
        return Result.ok(providerService.create(request));
    }

    @GetMapping
    public Result<PageResult<ProviderDetailResponse>> list(ProviderQueryRequest request) {
        return providerService.list(request);
    }

    @GetMapping("/{id}")
    public Result<ProviderDetailResponse> detail(@PathVariable Long id) {
        return Result.ok(providerService.getDetail(id));
    }

    @PutMapping("/{id}")
    public Result<Provider> update(@PathVariable Long id,
                                   @Valid @RequestBody ProviderUpdateRequest request) {
        return Result.ok(providerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        providerService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/test-connection")
    public Result<ConnectionTestResult> testConnection(@PathVariable Long id) {
        return Result.ok(connectionTestService.testById(id));
    }
}
