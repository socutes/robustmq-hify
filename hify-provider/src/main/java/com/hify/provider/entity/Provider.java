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
@TableName(value = "provider", autoResultMap = true)
public class Provider extends BaseEntity {

    /** 展示名称，唯一 */
    private String name;

    /**
     * 供应商类型
     * OPENAI / ANTHROPIC / OLLAMA / AZURE_OPENAI / OPENAI_COMPATIBLE
     */
    private String type;

    /** API 基础地址 */
    private String baseUrl;

    /** 鉴权配置，结构随 type 变化，加密存储 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> authConfig;

    /** 备注 */
    private String description;

    /** 是否启用 */
    private Integer enabled;
}
