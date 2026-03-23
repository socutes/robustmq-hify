package com.hify.agent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AgentToolBindRequest {

    @NotNull(message = "工具列表不能为 null，清空请传 []")
    private List<Long> toolIds;
}
