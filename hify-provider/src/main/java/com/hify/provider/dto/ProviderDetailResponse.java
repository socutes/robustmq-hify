package com.hify.provider.dto;

import com.hify.provider.entity.ModelConfig;
import com.hify.provider.entity.Provider;
import com.hify.provider.entity.ProviderHealth;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProviderDetailResponse {

    private Long id;
    private String name;
    private String type;
    private String baseUrl;
    private String description;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** authConfig 不返回给前端（含密钥），仅表示是否已配置 */
    private boolean authConfigured;

    private List<ModelConfig> models;
    private ProviderHealth health;

    public static ProviderDetailResponse from(Provider provider, List<ModelConfig> models, ProviderHealth health) {
        ProviderDetailResponse resp = new ProviderDetailResponse();
        resp.setId(provider.getId());
        resp.setName(provider.getName());
        resp.setType(provider.getType());
        resp.setBaseUrl(provider.getBaseUrl());
        resp.setDescription(provider.getDescription());
        resp.setEnabled(provider.getEnabled());
        resp.setCreatedAt(provider.getCreatedAt());
        resp.setUpdatedAt(provider.getUpdatedAt());
        resp.setAuthConfigured(provider.getAuthConfig() != null && !provider.getAuthConfig().isEmpty());
        resp.setModels(models);
        resp.setHealth(health);
        return resp;
    }
}
