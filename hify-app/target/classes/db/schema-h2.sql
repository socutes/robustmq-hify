-- H2 内存库建表，仅用于 mock profile
-- JSON 列用 CLOB 代替（H2 不支持 MySQL JSON 类型）

CREATE TABLE IF NOT EXISTS provider (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)    NOT NULL,
    type        VARCHAR(30)     NOT NULL,
    base_url    VARCHAR(500)    NOT NULL,
    auth_config CLOB            NOT NULL,
    description VARCHAR(500)    DEFAULT '',
    enabled     TINYINT         NOT NULL DEFAULT 1,
    deleted     TINYINT         NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL,
    updated_at  DATETIME        NOT NULL
);

CREATE TABLE IF NOT EXISTS model_config (
    id           BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    provider_id  BIGINT          NOT NULL,
    name         VARCHAR(100)    NOT NULL,
    model_id     VARCHAR(100)    NOT NULL,
    context_size INT             NOT NULL DEFAULT 4096,
    extra_params CLOB,
    enabled      TINYINT         NOT NULL DEFAULT 1,
    deleted      TINYINT         NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL,
    updated_at   DATETIME        NOT NULL
);

CREATE TABLE IF NOT EXISTS provider_health (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    provider_id     BIGINT          NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'UNKNOWN',
    last_check_at   DATETIME,
    last_success_at DATETIME,
    fail_count      INT             NOT NULL DEFAULT 0,
    latency_ms      INT,
    error_message   VARCHAR(500),
    updated_at      DATETIME        NOT NULL
);

CREATE TABLE IF NOT EXISTS mcp_server (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)    NOT NULL,
    endpoint    VARCHAR(500)    NOT NULL,
    description VARCHAR(500)    DEFAULT '',
    enabled     TINYINT         NOT NULL DEFAULT 1,
    deleted     TINYINT         NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL,
    updated_at  DATETIME        NOT NULL
);

CREATE TABLE IF NOT EXISTS agent (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    model_config_id BIGINT          NOT NULL,
    system_prompt   CLOB            DEFAULT '',
    description     VARCHAR(500)    DEFAULT '',
    enabled         TINYINT         NOT NULL DEFAULT 1,
    deleted         TINYINT         NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL,
    updated_at      DATETIME        NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_tool (
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    agent_id      BIGINT      NOT NULL,
    mcp_server_id BIGINT      NOT NULL,
    created_at    DATETIME    NOT NULL,
    updated_at    DATETIME    NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_session (
    id         BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    agent_id   BIGINT          NOT NULL,
    title      VARCHAR(200)    DEFAULT '',
    deleted    TINYINT         NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL,
    updated_at DATETIME        NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_message (
    id         BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT          NOT NULL,
    role       VARCHAR(20)     NOT NULL,
    content    CLOB            NOT NULL,
    tokens     INT             DEFAULT 0,
    deleted    TINYINT         NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL,
    updated_at DATETIME        NOT NULL
);
