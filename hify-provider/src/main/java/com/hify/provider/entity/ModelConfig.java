package com.hify.provider.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@TableName(value = "model_config", autoResultMap = true)
public class ModelConfig extends BaseEntity {

    /** 所属供应商 id */
    private Long providerId;

    /** 展示名称，如 "GPT-4o" */
    private String name;

    /**
     * 调用 API 时实际传递的模型标识
     * OpenAI: "gpt-4o"
     * Anthropic: "claude-3-5-sonnet-20241022"
     * Azure: deployment name
     * Ollama: "llama3"
     */
    private String modelId;

    /** 上下文窗口大小（token） */
    private Integer contextSize;

    /**
     * 模型级别扩展参数
     * 示例：{"maxTokens": 4096}、{"keepAlive": "10m"}
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraParams;

    /** 是否启用 */
    private Integer enabled;
}
