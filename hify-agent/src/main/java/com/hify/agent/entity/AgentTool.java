package com.hify.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Agent 与 MCP Server 的关联表，无逻辑删除（直接物理删除）
 */
@Getter
@Setter
@TableName("agent_tool")
public class AgentTool {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long agentId;

    private Long mcpServerId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
