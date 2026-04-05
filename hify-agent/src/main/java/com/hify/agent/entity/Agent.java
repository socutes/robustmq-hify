package com.hify.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@TableName("agent")
public class Agent extends BaseEntity {

    private String name;

    private String description;

    private String systemPrompt;

    private Long modelConfigId;

    /** 0.00~1.00 */
    private BigDecimal temperature;

    private Integer maxTokens;

    private Integer maxContextTurns;

    private Integer enabled;

    /** 绑定的知识库 id，NULL 表示不启用 RAG */
    private Long knowledgeBaseId;
}
