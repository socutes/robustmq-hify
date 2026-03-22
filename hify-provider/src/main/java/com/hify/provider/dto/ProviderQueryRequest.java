package com.hify.provider.dto;

import lombok.Data;

@Data
public class ProviderQueryRequest {

    private String type;
    private Integer enabled;
    private Integer page = 1;
    private Integer pageSize = 20;
}
