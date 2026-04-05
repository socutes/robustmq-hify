package com.hify.knowledge.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@TableName("knowledge_base")
public class KnowledgeBase extends BaseEntity {
    private String name;
    private String description;
    private Integer enabled;
}
