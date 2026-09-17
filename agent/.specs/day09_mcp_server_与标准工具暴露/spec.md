# Day 9 Spec — MCP Server 与标准工具暴露

## Goal
把 Day 8 已有的金融工具能力（getStockPrice / getFundamental）通过 MCP 标准协议暴露，
使外部 Agent/Host 能发现和调用；同时 Agent 自身也能作为 MCP Client 消费远端工具。
核心纪律：**不复制工具逻辑，通过 bridge 复用现有 StockTools Bean + 护栏链**。

## Architecture Decision（已拍板）
- **不新建项目、不裸线程**：MCP Server 作为 Spring Boot 进程内的一个 Bean，与 webflux 共享 JVM。
- **传输**：HTTP/SSE（不选 stdio，避免与 webflux stdin/stdout 冲突），挂在 `/mcp`。
- **复用**：`McpToolBridge` 把现有 `StockTools` 的 `@Tool` 方法桥接为 MCP 工具注册，
  业务逻辑零重复；护栏链（限流/缓存/降级）对内部图调用和外部 MCP 调用统一生效。

## Scope
- In scope:
  - 新增 `langchain4j-mcp:1.20.0` 依赖。
  - `infrastructure/mcp/McpServerBridge.java`：启动 MCP Server（HTTP/SSE），注册工具。
  - `infrastructure/mcp/McpToolProvider.java`：把 `StockTools` 能力适配为 MCP 工具定义（schema 生成）。
  - `infrastructure/mcp/McpClientGateway.java`（可选最小集）：Agent 作为 MCP Client 消费远端工具。
  - 配置：`AiServicesConfiguration` 装配 MCP Server Bean。
  - 单元测试：工具 schema 生成、bridge 适配、MCP 调用往返（fixture/mock，不打真网络）。
- Out of scope:
  - 大重构、新建独立工程。
  - MCP Resource / Prompt 暴露（本期只暴露 Tool，最小切片）。
  - 生产级认证/多租户。

## Contract
- MCP 工具名与现有 `@Tool` 名一致：`getStockPrice` / `getFundamental`。
- inputSchema 为合法 JSON Schema（symbol: string, required）。
- 工具执行失败返回 MCP 标准 error，不暴露 Java 堆栈。
- 护栏链行为不变：限流/降级对外部 MCP 调用同样生效（degraded 标记透传）。

## Definition of Done
见 checklist.md。

## Vertical Evolution Contract

### Capability
MCP Capability

### Before
Day 8 Tool 只通过本地 Tool registry 调用。

### After
同一工具能力可通过 MCP 标准协议被外部 Agent/Host 发现和调用；Agent 亦可作为 MCP Client。

### Reuse-first
复用 Day 1~8 的 StockTools、MarketDataGateway、护栏链、领域对象、测试基建。业务逻辑零复制。

### Regression
完成后验证 Day 1/3/4/7/8 核心能力不退化，证据记入 checklist.md。
