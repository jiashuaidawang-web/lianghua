# Day 9 Tasks

- [x] 读 constitution + Day 1~8 工程，建立能力地图。
- [x] 拍板架构：同 JVM 内嵌 MCP Server（HTTP/SSE），bridge 复用 StockTools。
- [x] 查 LangChain4j 1.20.0 MCP API → 发现仅有 beta 且只有 Client；决定自研 Server+Client（零 beta）。
- [x] 加 `maven.compiler.parameters=true`（bridge 反射参数名生成 JSON Schema）。
- [x] 实现 MCP 协议模型：JsonRpc 信封 + McpProtocol 业务消息（records + Jackson）。
- [x] 实现 `McpServer`：纯逻辑，处理 initialize / tools/list / tools/call。
- [x] 实现 `McpToolBridge`：反射 @Tool/@P 生成 schema，零复制复用。
- [x] 实现 `McpClient`：WebClient 调远端 MCP（自序列化避免 JsonNode toString 坑）。
- [x] 实现 `McpController`：webflux /mcp 端点。
- [x] `AiServicesConfiguration` 装配 MCP Bean。
- [x] 单元测试：McpServerTest (11)、McpToolBridgeTest (4)。
- [x] 端到端测试：McpEndToEndTest (4) —— JDK HttpServer + 真实 McpClient 往返。
- [x] `mvn clean test` 全量回归通过（93 tests, 0 failures）。
- [x] 更新 checklist.md + 证据。

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
