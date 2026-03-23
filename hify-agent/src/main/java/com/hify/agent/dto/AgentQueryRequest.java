package com.hify.agent.dto;

import lombok.Data;

@Data
public class AgentQueryRequest {

    private int page = 1;
    private int pageSize = 20;
    private Boolean enabled;
}
