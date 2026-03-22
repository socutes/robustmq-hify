-- Hify DDL
-- 字符集：utf8mb4，主键 bigint 自增，逻辑删除 deleted tinyint(1)
-- 外键在应用层维护，不建数据库级外键约束

CREATE DATABASE IF NOT EXISTS hify DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE hify;

-- ─────────────────────────────────────────────
-- 模型提供商
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS provider (
    id          BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(100)    NOT NULL                COMMENT '提供商名称，如 OpenAI',
    type        VARCHAR(30)     NOT NULL                COMMENT '类型：OPENAI / ANTHROPIC / AZURE_OPENAI / GOOGLE / CUSTOM',
    base_url    VARCHAR(500)    NOT NULL                COMMENT 'API 基础地址',
    description VARCHAR(500)    DEFAULT ''              COMMENT '备注',
    enabled     TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '是否启用：1 启用 0 禁用',
    deleted     TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 正常 1 删除',
    created_at  DATETIME        NOT NULL                COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL                COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY idx_provider_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型提供商';

-- ─────────────────────────────────────────────
-- 模型配置（隶属于提供商）
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS model_config (
    id           BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    provider_id  BIGINT          NOT NULL                COMMENT '所属提供商 id',
    name         VARCHAR(100)    NOT NULL                COMMENT '模型展示名称',
    model_id     VARCHAR(100)    NOT NULL                COMMENT '调用时使用的模型标识，如 gpt-4o',
    api_key      VARCHAR(500)    DEFAULT ''              COMMENT 'API Key（加密存储）',
    context_size INT             NOT NULL DEFAULT 4096   COMMENT '上下文窗口大小（token）',
    enabled      TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '是否启用',
    deleted      TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at   DATETIME        NOT NULL                COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL                COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_model_config_provider_id (provider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型配置';

-- ─────────────────────────────────────────────
-- MCP Server（工具服务）
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS mcp_server (
    id           BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    name         VARCHAR(100)    NOT NULL                COMMENT 'MCP Server 名称',
    endpoint     VARCHAR(500)    NOT NULL                COMMENT '服务端点地址',
    description  VARCHAR(500)    DEFAULT ''              COMMENT '描述',
    enabled      TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '是否启用',
    deleted      TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at   DATETIME        NOT NULL                COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL                COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY idx_mcp_server_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 工具服务';

-- ─────────────────────────────────────────────
-- Agent 配置
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS agent (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    name            VARCHAR(100)    NOT NULL                COMMENT 'Agent 名称',
    model_config_id BIGINT          NOT NULL                COMMENT '绑定的模型配置 id',
    system_prompt   TEXT            DEFAULT ''              COMMENT 'System Prompt',
    description     VARCHAR(500)    DEFAULT ''              COMMENT '描述',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '是否启用',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at      DATETIME        NOT NULL                COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL                COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY idx_agent_name (name),
    KEY idx_agent_model_config_id (model_config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 配置';

-- ─────────────────────────────────────────────
-- Agent 与 MCP Server 关联（M:N）
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS agent_tool (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    agent_id      BIGINT      NOT NULL                COMMENT 'Agent id',
    mcp_server_id BIGINT      NOT NULL                COMMENT 'MCP Server id',
    created_at    DATETIME    NOT NULL                COMMENT '创建时间',
    updated_at    DATETIME    NOT NULL                COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY idx_agent_tool_agent_mcp (agent_id, mcp_server_id),
    KEY idx_agent_tool_mcp_server_id (mcp_server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 绑定工具';

-- ─────────────────────────────────────────────
-- 对话会话
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS chat_session (
    id         BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    agent_id   BIGINT          NOT NULL                COMMENT '所属 Agent id',
    title      VARCHAR(200)    DEFAULT ''              COMMENT '会话标题',
    deleted    TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at DATETIME        NOT NULL                COMMENT '创建时间',
    updated_at DATETIME        NOT NULL                COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_chat_session_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话';

-- ─────────────────────────────────────────────
-- 对话消息（增长最快，注意分页查询性能）
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS chat_message (
    id         BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id BIGINT          NOT NULL                COMMENT '所属会话 id',
    role       VARCHAR(20)     NOT NULL                COMMENT '消息角色：user / assistant / system',
    content    LONGTEXT        NOT NULL                COMMENT '消息内容',
    tokens     INT             DEFAULT 0               COMMENT '消耗 token 数',
    deleted    TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    created_at DATETIME        NOT NULL                COMMENT '创建时间',
    updated_at DATETIME        NOT NULL                COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_chat_message_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息';
