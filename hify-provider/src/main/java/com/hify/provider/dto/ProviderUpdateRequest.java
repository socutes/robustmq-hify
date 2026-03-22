package com.hify.provider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class ProviderUpdateRequest {

    @NotBlank(message = "提供商名称不能为空")
    private String name;

    @NotBlank(message = "Base URL 不能为空")
    private String baseUrl;

    @NotNull(message = "鉴权配置不能为空")
    private Map<String, Object> authConfig;

    private String description;
}
