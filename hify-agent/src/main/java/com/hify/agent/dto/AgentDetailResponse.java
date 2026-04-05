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

    /** 绑定的知识库 id */
    private Long knowledgeBaseId;

    /** 绑定的工作流 id */
    private Long workflowId;

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
        resp.setKnowledgeBaseId(agent.getKnowledgeBaseId());
        resp.setWorkflowId(agent.getWorkflowId());
        resp.setCreatedAt(agent.getCreatedAt());
        resp.setUpdatedAt(agent.getUpdatedAt());
        resp.setToolIds(toolIds);
        return resp;
    }
}
