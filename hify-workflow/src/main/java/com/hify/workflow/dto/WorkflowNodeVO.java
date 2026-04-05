package com.hify.workflow.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.workflow.entity.WorkflowNode;
import lombok.Data;

@Data
public class WorkflowNodeVO {
    private Long id;
    private String nodeKey;
    private String type;
    private String name;
    private JsonNode config;

    public static WorkflowNodeVO from(WorkflowNode node, ObjectMapper mapper) {
        WorkflowNodeVO vo = new WorkflowNodeVO();
        vo.setId(node.getId());
        vo.setNodeKey(node.getNodeKey());
        vo.setType(node.getType());
        vo.setName(node.getName());
        try {
            String cfg = node.getConfig();
            vo.setConfig(cfg != null && !cfg.isBlank()
                    ? mapper.readTree(cfg)
                    : mapper.createObjectNode());
        } catch (Exception e) {
            vo.setConfig(mapper.createObjectNode());
        }
        return vo;
    }
}
