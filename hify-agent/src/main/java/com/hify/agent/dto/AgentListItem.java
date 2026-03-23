package com.hify.agent.dto;

import com.hify.agent.entity.Agent;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AgentListItem {

    private Long id;
    private String name;
    private String description;
    private Long modelConfigId;
    private BigDecimal temperature;
    private Integer enabled;
    private int toolCount;
    private LocalDateTime createdAt;

    public static AgentListItem from(Agent agent, int toolCount) {
        AgentListItem item = new AgentListItem();
        item.setId(agent.getId());
        item.setName(agent.getName());
        item.setDescription(agent.getDescription());
        item.setModelConfigId(agent.getModelConfigId());
        item.setTemperature(agent.getTemperature());
        item.setEnabled(agent.getEnabled());
        item.setToolCount(toolCount);
        item.setCreatedAt(agent.getCreatedAt());
        return item;
    }
}
