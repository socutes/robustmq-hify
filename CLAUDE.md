# Hify 项目规范

## 项目概述

Hify 是一个简版的 AI Agent 开发平台（参考 Dify），可本地部署，面向团队内部小规模使用（20-50 人同时在线）。

技术栈：Spring Boot 3.x + MyBatis-Plus + MySQL 8.x + Redis 7.x + pgvector
前端：Vue 3 + TypeScript + Element Plus + Vite
容器化：Docker + K8s

### 做什么
- 多模型提供商管理（OpenAI、Claude、Gemini、Ollama）
- Agent 创建与配置（绑模型、绑工具、System Prompt）
- 对话引擎（流式响应、多轮对话、上下文管理）
- RAG 知识库（文档上传、向量检索、引用回答）
- 简版工作流编排（顺序执行、条件分支）
- MCP 工具接入（Agent 可调用外部工具）
- 管理控制台（前端界面）

### 不做什么
- 不做可视化工作流拖拽编排
- 不做多租户 / 权限体系
- 不做插件市场、计费系统
- 不做消息推送、WebSocket 长连接（用 SSE 替代）

---

## 架构

### 整体结构
模块化单体。一个 Spring Boot 应用，Maven 多模块组织。
模块间通过 Service 接口调用，不直接引用其他模块的实现类，为后续微服务拆分留口子。

### 模块划分
```
hify/
├── hify-app/            # 启动模块，Spring Boot Application
├── hify-provider/       # 模型提供商管理
├── hify-agent/          # Agent 管理与配置
├── hify-chat/           # 对话引擎
├── hify-mcp/            # MCP 工具管理与调用
├── hify-workflow/       # 工作流编排与执行
├── hify-knowledge/      # 知识库与 RAG
├── hify-common/         # 公共模块（工具类、常量、异常、DTO 基类）
├── hify-web/            # Vue 前端
└── deploy/              # Docker + K8s 部署配置
```

### 模块依赖关系
- hify-chat → hify-agent, hify-provider（对话时读取 Agent 配置和模型配置）
- hify-chat → hify-workflow（对话可能触发工作流）
- hify-chat → hify-knowledge（对话可能走 RAG 检索）
- hify-agent → hify-mcp（Agent 绑定工具）
- 所有业务模块 → hify-common
- hify-app → 所有业务模块（启动入口）

### 外部调用处理
- LLM 调用使用独立线程池（llmExecutor），和业务请求隔离
- Resilience4j 熔断，每个提供商独立熔断器
  - slidingWindowSize: 10
  - failureRateThreshold: 50%
  - waitDurationInOpenState: 30s
- 对话接口 60s 超时，连通性测试 10s 超时
- 重试策略按异常类型区分：网络抖动重试，认证失败不重试，限流退避重试
- 流式响应使用 SseEmitter + 独立线程池

### 缓存策略
- Provider / Agent 配置：Redis Cache-Aside，TTL 30min
- 对话上下文：Redis，TTL 2h，key = session:{sessionId}
- 对话消息持久化走 MySQL，不缓存
- MCP Server 配置数量少，不缓存

### 部署架构（当前 50 人规模）
Nginx（静态文件 + API 反向代理）→ Spring Boot → MySQL / Redis / pgvector / LLM API
- Nginx 配置 SSE 需要 proxy_buffering off
- 全部组件 Docker 镜像化，K8s 部署
- 本地开发直接 java -jar + npm run dev

### 扩展路径（几千人规模，当前不做但不堵死）
1. chat 模块拆分独立部署，水平扩展
2. MySQL 读写分离
3. Redis 单实例 → 集群
4. 引入消息队列做异步处理（对话日志异步写入）

---

## 代码组织

### 后端模块内部结构
每个业务模块统一结构：
```
src/main/java/com/hify/{module}/
├── controller/        # REST 接口，只做参数校验和调用 Service
├── service/           # 业务逻辑接口
├── service/impl/      # 业务逻辑实现
├── mapper/            # MyBatis-Plus Mapper
├── entity/            # 数据库实体
├── dto/               # 请求/响应对象
├── config/            # 模块级配置类
├── exception/         # 模块级自定义异常
└── constant/          # 模块级常量
```

### 分层规则
- Controller 只做参数校验和调用 Service，不写业务逻辑
- Service 处理所有业务逻辑
- 跨模块调用走 Service 接口，不直接引用其他模块的 Mapper 或 Entity
- 公共工具类、基类放 hify-common

### 前端结构
```
hify-web/src/
├── api/               # 接口调用，按模块分文件
├── components/        # 公共组件
├── composables/       # 组合式函数
├── router/            # 路由配置
├── types/             # TypeScript 类型定义
├── utils/             # 工具函数（request.ts 等）
├── views/             # 页面组件，按模块分目录
│   ├── provider/
│   ├── agent/
│   └── chat/
└── App.vue
```

---

## 数据库规范

- 表名：小写下划线，不加前缀。例如 provider、chat_message
- 字段名：小写下划线。例如 api_key、base_url
- 主键：统一用 id，bigint 自增
- 时间字段：created_at、updated_at，datetime 类型
- 逻辑删除：deleted，tinyint，0=正常 1=删除
- 索引命名：idx_{表名}_{字段名}
- 所有外键在应用层维护，不建数据库级外键约束
- 字符集：utf8mb4
- 大表预判：chat_message 增长最快，提前考虑分页查询性能
- 分页查询避免 SELECT COUNT(*)，用游标分页或估算总数

### 核心数据模型
```
provider (模型提供商)
  └── model_config (模型配置) [1:N]

agent (Agent 配置)
  ├── → model_config [N:1]
  ├── ↔ mcp_server (MCP 工具) [M:N, 通过 agent_tool]
  └── → chat_session (对话会话) [1:N]
        └── → chat_message (对话消息) [1:N]

workflow (工作流定义)
  ├── → workflow_node (节点) [1:N]
  └── → workflow_edge (连线) [1:N]

knowledge_base (知识库)
  └── → document (文档) [1:N]
        └── → document_chunk (文档分块) [1:N]
```

具体表字段详见 docs/er-diagram.md

---

## 接口规范

### 路径
RESTful 风格：/api/v1/{资源复数名}
```
GET    /api/v1/providers          # 列表（分页）
POST   /api/v1/providers          # 创建
GET    /api/v1/providers/{id}     # 详情
PUT    /api/v1/providers/{id}     # 更新
DELETE /api/v1/providers/{id}     # 删除
POST   /api/v1/providers/{id}/test-connection  # 非 CRUD 操作用动词
```

### 统一响应
所有接口返回 Result<T>：
```json
{ "code": 200, "message": "success", "data": {...} }
```

### 分页
- 请求：page（从 1 开始）、pageSize（默认 20，最大 100）
- 响应：Result<PageResult<T>>，PageResult 包含 list、total、page、pageSize

### 空值处理
- 列表字段空时返回 []，不返回 null
- 字符串字段空时返回 ""，不返回 null
- 对象不存在时返回 null

### 错误码
四位数字，按模块分段：
```
1000-1999  通用（参数错误、未授权、系统内部错误等）
2000-2999  Provider
3000-3999  Agent
4000-4999  Chat
5000-5999  MCP
6000-6999  Workflow
7000-7999  Knowledge
```

---

## 行为指令

### 写代码时
- 每个功能用最简单直接的方式实现
- 不引入不必要的设计模式，除非我明确要求
- 不做过度抽象，不过度工程化
- 不引入技术栈以外的依赖，需要时先问我
- 所有外部调用必须有超时设置
- 配置项外化到 application.yml，不硬编码
- 异常处理必须使用 ErrorCode 枚举，禁止硬编码错误码和错误信息

### 改代码时
- 先理解相关模块的设计意图
- 不要为了新功能破坏已有接口契约
- 改完确保已有测试通过

### 不确定时
- 架构选择给我 2-3 个方案对比，我来拍板
- 规范没覆盖的情况，先问我，不要自己编规矩

### 线程池使用
- LLM 调用必须使用 @Qualifier("llmExecutor") 注入的线程池
- 异步非关键任务使用 @Qualifier("asyncExecutor")
- 禁止在业务代码中 new Thread() 或使用默认线程池