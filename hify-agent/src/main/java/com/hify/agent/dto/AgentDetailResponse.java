package com.hify.agent.dto;

import com.hify.agent.entity.Agent;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AgentDetailResponse {

    private Long id;
    private String name;
    private String description;
    private String systemPrompt;
    private Long modelConfigId;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Integer maxContextTurns;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 绑定的 MCP Server id 列表 */
    private List<Long> toolIds;

    public static AgentDetailResponse from(Agent agent, List<Long> toolIds) {
        AgentDetailResponse resp = new AgentDetailResponse();
        resp.setId(agent.getId());
        resp.setName(agent.getName());
        resp.setDescription(agent.getDescription());
        resp.setSystemPrompt(agent.getSystemPrompt());
        resp.setModelConfigId(agent.getModelConfigId());
        resp.setTemperature(agent.getTemperature());
        resp.setMaxTokens(agent.getMaxTokens());
        resp.setMaxContextTurns(agent.getMaxContextTurns());
        resp.setEnabled(agent.getEnabled());
        resp.setCreatedAt(agent.getCreatedAt());
        resp.setUpdatedAt(agent.getUpdatedAt());
        resp.setToolIds(toolIds);
        return resp;
    }
}
