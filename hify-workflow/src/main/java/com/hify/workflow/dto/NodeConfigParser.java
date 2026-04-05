package com.hify.workflow.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NodeConfigParser {

    private final ObjectMapper objectMapper;

    public NodeConfigDef parse(String type, String configJson) {
        try {
            String json = configJson != null ? configJson : "{}";
            return switch (type) {
                case "START"     -> objectMapper.readValue(json, NodeConfigDef.StartConfig.class);
                case "LLM"       -> objectMapper.readValue(json, NodeConfigDef.LlmConfig.class);
                case "CONDITION" -> objectMapper.readValue(json, NodeConfigDef.ConditionConfig.class);
                case "API_CALL"  -> objectMapper.readValue(json, NodeConfigDef.ApiCallConfig.class);
                case "KNOWLEDGE" -> objectMapper.readValue(json, NodeConfigDef.KnowledgeConfig.class);
                case "END"       -> objectMapper.readValue(json, NodeConfigDef.EndConfig.class);
                default          -> throw new IllegalArgumentException("未知节点类型: " + type);
            };
        } catch (Exception e) {
            throw new RuntimeException("节点配置解析失败 type=" + type + ": " + e.getMessage(), e);
        }
    }
}
