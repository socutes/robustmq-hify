package com.hify.workflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 各节点类型配置的 sealed 父接口。
 * 执行引擎用 switch 模式匹配分发，编译器保证穷举。
 */
public sealed interface NodeConfigDef
        permits NodeConfigDef.StartConfig,
                NodeConfigDef.LlmConfig,
                NodeConfigDef.ConditionConfig,
                NodeConfigDef.ApiCallConfig,
                NodeConfigDef.KnowledgeConfig,
                NodeConfigDef.EndConfig {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StartConfig() implements NodeConfigDef {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LlmConfig(Long modelConfigId, String prompt, String outputVariable) implements NodeConfigDef {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ConditionConfig(String expression, String outputVariable) implements NodeConfigDef {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiCallConfig(String url, String method, String outputVariable) implements NodeConfigDef {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KnowledgeConfig(Long knowledgeBaseId, String query, Integer topK, String outputVariable)
            implements NodeConfigDef {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EndConfig(String outputVariable) implements NodeConfigDef {}
}
