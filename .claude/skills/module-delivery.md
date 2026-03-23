# Skill: 业务模块全流程交付

触发方式：当用户说"开发 XX 模块"、"实现 XX 功能"、"交付 XX" 时，按此流程推进。

---

## 总体原则

- 每步有明确产出物，编译或验证通过再进下一步
- 关键设计决策必须等用户确认，不自行拍板
- 先咨询后实现，先后端后前端

---

## Step 1 — 咨询与设计（不写代码）

**目标**：对齐需求边界和数据模型，避免返工。

**产出物**：
- 候选方案对比表（2-3 个方案，标明取舍）
- 数据模型草稿（表名、核心字段、关联关系）
- 接口清单（Method + Path + 简要说明）

**流程**：
1. 分析业务需求，列出候选技术方案
2. 给出推荐方案及理由
3. 提出需要用户决策的问题（如：是否需要软删除？JSON 字段还是关联表？）

> ⚠️ **等待用户确认**：数据模型和接口设计确认后再进入 Step 2

**注意事项**：
- JSON 字段（如 auth_config）需要用 `@TableName(autoResultMap = true)` + `JacksonTypeHandler`，否则反序列化为 null
- 高频写入的表（如 health 记录）不要继承 BaseEntity（避免逻辑删除和审计字段的写放大）
- 敏感字段（如 api_key）不能出现在任何响应 DTO 中，用 `authConfigured: boolean` 代替

---

## Step 2 — 更新 schema.sql

**目标**：数据库 DDL 与设计对齐。

**产出物**：
- `hify-app/src/main/resources/db/schema.sql` 新增表 DDL
- `hify-app/src/main/resources/db/schema-h2.sql` H2 兼容版本（JSON → CLOB）

**验证**：
```bash
# 用 mock profile 启动，H2 会自动执行 schema-h2.sql
java -jar hify-app/target/hify-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=mock
# 访问 H2 控制台确认表已创建
open http://localhost:8080/h2-console
```

**注意事项**：
- H2 不支持 `JSON` 类型，必须用 `CLOB` 替代，且两个 schema 文件都要维护
- 索引命名规范：`idx_{表名}_{字段名}`
- 所有外键在应用层维护，不建数据库级外键约束

---

## Step 3 — Entity + Mapper

**目标**：ORM 层与数据库表对齐。

**产出物**：
- `entity/XxxEntity.java`（继承 BaseEntity 或独立）
- `mapper/XxxMapper.java`（继承 BaseMapper，复杂查询加 `@Select`）

**验证**：
```bash
mvn clean install -DskipTests -pl hify-{module} -am
```

**注意事项**：
- 有 JSON 字段的 Entity 必须加 `@TableName(autoResultMap = true)`，字段上加 `@TableField(typeHandler = JacksonTypeHandler.class)`
- MyBatis-Plus 3.5.9 分页插件在独立模块 `mybatis-plus-jsqlparser`，缺少会导致 `PaginationInnerInterceptor` 找不到
- 自定义查询方法返回 `Optional<T>` 时用 `@Select` + default 方法封装

---

## Step 4 — DTO

**目标**：定义请求/响应对象，隔离内部实体。

**产出物**：
- `dto/XxxCreateRequest.java`（`@Valid` 校验注解）
- `dto/XxxUpdateRequest.java`
- `dto/XxxQueryRequest.java`（分页参数继承或包含 page/pageSize）
- `dto/XxxDetailResponse.java`（静态工厂方法 `from(entity, ...)`）

**注意事项**：
- 响应 DTO 不能暴露 authConfig / password 等敏感字段
- 分页响应统一用 `PageResult.of(list, total, page, pageSize)`，返回 `Result<PageResult<T>>`
  - ⚠️ 不要让 `PageResult` 继承 `Result`，否则序列化后 `data` 字段是数组，`total` 在外层，前端拦截器解包后丢失 `total`
- `PageResult` 正确结构：`{ "data": { "list": [...], "total": N, "page": 1, "pageSize": 20 } }`

---

## Step 5 — Service（业务逻辑）

**目标**：实现核心业务，接口与实现分离。

**产出物**：
- `service/XxxService.java`（接口）
- `service/impl/XxxServiceImpl.java`（实现）

**流程**：
1. CRUD 基础逻辑（含唯一性校验、级联查询）
2. 特殊业务逻辑（如连通性测试、健康检查）
3. 缓存注解（`@Cacheable` / `@CacheEvict`）

**验证**：
```bash
mvn clean install -DskipTests -pl hify-{module} -am
```

**注意事项**：
- 跨模块调用走 Service 接口，不直接引用其他模块的 Mapper 或 Entity
- 外部 HTTP 调用（如 LLM API 连通性测试）必须设超时，用 `LlmHttpClient` 的 `get(url, headers, testClient)`
- 健康检查定时任务加 `@ConditionalOnProperty(name = "hify.health-check.enabled", havingValue = "true", matchIfMissing = true)`，mock profile 设为 false
- 使用 `@Qualifier("llmExecutor")` 注入线程池，禁止 `new Thread()` 或默认线程池

---

## Step 6 — Controller

**目标**：暴露 REST 接口，只做参数校验和 Service 调用。

**产出物**：
- `controller/XxxController.java`

**验证**：
```bash
mvn clean install -DskipTests
java -jar hify-app/target/hify-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=mock
```

逐条跑 curl：
```bash
# 创建
curl -s -X POST http://localhost:8080/api/v1/{resource} \
  -H 'Content-Type: application/json' \
  -d '{...}' | jq .

# 列表
curl -s 'http://localhost:8080/api/v1/{resource}?page=1&pageSize=10' | jq .

# 详情
curl -s http://localhost:8080/api/v1/{resource}/1 | jq .

# 更新
curl -s -X PUT http://localhost:8080/api/v1/{resource}/1 \
  -H 'Content-Type: application/json' \
  -d '{...}' | jq .

# 删除
curl -s -X DELETE http://localhost:8080/api/v1/{resource}/1 | jq .
```

> ⚠️ **等待用户确认**：所有 curl 返回预期结果后再进入前端对接

**注意事项**：
- Spring Boot 3.2 必须在 `maven-compiler-plugin` 加 `<parameters>true</parameters>`，否则 `@PathVariable Long id` 参数名无法识别，导致 400 错误
- Controller 只调用 Service，不写业务逻辑，不直接操作 Mapper

---

## Step 7 — 前端 API 文件

**目标**：封装后端接口，定义 TypeScript 类型。

**产出物**：
- `hify-web/src/api/{module}.ts`

**内容**：
- 请求/响应类型定义（与后端 DTO 字段对齐）
- 导出各接口方法（使用 `request.ts` 的 get/post/put/del）

**注意事项**：
- 前端 `request.ts` 拦截器会自动解包 `response.data.data`，API 方法的返回类型直接写业务数据类型，不需要包 `Result<T>`
- 列表接口返回类型写 `PageResult<T>`（包含 list/total/page/pageSize），对应后端解包后的 `data` 字段

---

## Step 8 — 前端页面对接

**目标**：替换 mock 数据，接入真实 API。

**产出物**：
- 更新 `views/{module}/XxxList.vue`

**流程**：
1. 把 HifyTable 的 `api` prop 换成真实 API 方法
2. 表单提交换成 create/update API
3. 删除换成 delete API + useConfirm
4. 按需添加操作按钮（如测试连接）
5. 按需添加状态列（健康状态、关联数量等）

**验证**：
```bash
# 确保后端已启动
java -jar hify-app/target/hify-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=mock

# 启动前端
cd hify-web && npm run dev
```

在浏览器 DevTools → Network 确认：
- 请求打到了后端（状态码 200，非 ERR_CONNECTION_REFUSED）
- 响应 `data.list` 是数组，`data.total` 是数字
- 表格有数据渲染（或显示"暂无数据"而非一直转圈）

> ⚠️ 如果页面一直转圈：先看 Network 标签确认请求状态码，再排查后端是否启动

**注意事项**：
- Vite 代理：`/api` → `http://localhost:8080`，前端 baseURL 设为 `/api`，后端路径 `/api/v1/xxx` 完整保留
- 前端 `env.d.ts` 不要写 `declare module '*.vue' { ... }`，会覆盖 Volar 的真实类型推断，导致组件 ref 的 expose 方法找不到

---

## 常见坑速查

| 现象 | 原因 | 修复 |
|------|------|------|
| 页面一直转圈 | 后端未启动 / 请求 pending | 先看 Network 状态码 |
| 列表有数据但 total=0 不显示分页 | PageResult 继承 Result 导致 data 是数组，total 在外层被拦截器丢弃 | PageResult 改为普通 POJO，data 包含 {list,total} |
| @PathVariable 400 错误 | 缺少 `-parameters` 编译参数 | pom.xml compiler plugin 加 `<parameters>true</parameters>` |
| JSON 字段反序列化 null | 缺少 autoResultMap=true 或 JacksonTypeHandler | Entity 加注解 |
| mock profile 启动失败 Bean 冲突 | RedisConfig 未排除 | 加 `@Profile("!mock")` |
| H2 启动报 SQL 错误 | schema.sql 用了 MySQL 专属语法（如 JSON 类型） | 维护独立 schema-h2.sql，JSON→CLOB |
| hify-common 改动后运行旧代码 | spring-boot:run 用了旧 jar | 改 hify-common 后必须先 `mvn install` |
