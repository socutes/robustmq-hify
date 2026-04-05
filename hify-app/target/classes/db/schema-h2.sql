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
    id                BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(100)    NOT NULL,
    description       VARCHAR(500)    DEFAULT '',
    system_prompt     CLOB            DEFAULT '',
    model_config_id   BIGINT          NOT NULL,
    temperature       DECIMAL(3,2)    NOT NULL DEFAULT 0.70,
    max_tokens        INT             NOT NULL DEFAULT 2048,
    max_context_turns INT             NOT NULL DEFAULT 10,
    enabled             TINYINT         NOT NULL DEFAULT 1,
    knowledge_base_id   BIGINT          DEFAULT NULL,
    workflow_id         BIGINT          DEFAULT NULL,
    deleted             TINYINT         NOT NULL DEFAULT 0,
    created_at          DATETIME        NOT NULL,
    updated_at          DATETIME        NOT NULL
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
    status     VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    deleted    TINYINT         NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL,
    updated_at DATETIME        NOT NULL
);

CREATE TABLE IF NOT EXISTS knowledge_base (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)    NOT NULL,
    description VARCHAR(500)    DEFAULT '',
    enabled     TINYINT         NOT NULL DEFAULT 1,
    deleted     TINYINT         NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL,
    updated_at  DATETIME        NOT NULL
);

CREATE TABLE IF NOT EXISTS document (
    id                BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    knowledge_base_id BIGINT          NOT NULL,
    name              VARCHAR(200)    NOT NULL,
    file_type         VARCHAR(20)     NOT NULL,
    file_size         BIGINT          NOT NULL,
    status            VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    error_message     VARCHAR(500)    DEFAULT '',
    chunk_count       INT             NOT NULL DEFAULT 0,
    deleted           TINYINT         NOT NULL DEFAULT 0,
    created_at        DATETIME        NOT NULL,
    updated_at        DATETIME        NOT NULL
);

CREATE TABLE IF NOT EXISTS workflow (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)    NOT NULL,
    description VARCHAR(500)    DEFAULT '',
    status      VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    deleted     TINYINT         NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL,
    updated_at  DATETIME        NOT NULL
);

CREATE TABLE IF NOT EXISTS workflow_node (
    id           BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workflow_id  BIGINT          NOT NULL,
    node_key     VARCHAR(100)    NOT NULL,
    type         VARCHAR(50)     NOT NULL,
    name         VARCHAR(100)    NOT NULL DEFAULT '',
    config       CLOB            DEFAULT '{}',
    deleted      TINYINT         NOT NULL DEFAULT 0,
    created_at   DATETIME        NOT NULL,
    updated_at   DATETIME        NOT NULL
);

CREATE TABLE IF NOT EXISTS workflow_edge (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workflow_id     BIGINT          NOT NULL,
    source_node_key VARCHAR(100)    NOT NULL,
    target_node_key VARCHAR(100)    NOT NULL,
    condition_expr  VARCHAR(500)    DEFAULT NULL,
    deleted         TINYINT         NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL,
    updated_at      DATETIME        NOT NULL
);

CREATE TABLE IF NOT EXISTS workflow_run (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workflow_id  BIGINT       NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',
    input        CLOB,
    output       CLOB,
    error        VARCHAR(500),
    elapsed_ms   INT,
    finished_at  DATETIME,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL
);

CREATE TABLE IF NOT EXISTS workflow_node_run (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    workflow_run_id BIGINT       NOT NULL,
    node_key        VARCHAR(100) NOT NULL,
    node_type       VARCHAR(50)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',
    outputs         CLOB,
    error           VARCHAR(500),
    elapsed_ms      INT,
    finished_at     DATETIME,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_message (
    id            BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id    BIGINT          NOT NULL,
    role          VARCHAR(20)     NOT NULL,
    content       CLOB            NOT NULL,
    tokens        INT             DEFAULT 0,
    finish_reason VARCHAR(50)     DEFAULT '',
    latency_ms    INT             DEFAULT 0,
    deleted       TINYINT         NOT NULL DEFAULT 0,
    created_at    DATETIME        NOT NULL,
    updated_at    DATETIME        NOT NULL
);
