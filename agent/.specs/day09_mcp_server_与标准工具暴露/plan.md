# Day 9 Plan

1. 加 `langchain4j-mcp:1.20.0` 依赖（pom.xml）。
2. 查 LangChain4j 1.20.0 MCP 官方文档/Javadoc，确认 McpServer/McpClient/McpToolSpec 的 API。
3. 实现 `infrastructure/mcp/McpToolProvider.java`：把 StockTools 能力适配为 MCP 工具定义 + JSON Schema。
4. 实现 `infrastructure/mcp/McpServerBridge.java`：启动 MCP Server（HTTP/SSE 传输，/mcp），注册工具。
5. （最小集）`infrastructure/mcp/McpClientGateway.java`：Agent 作为 MCP Client 连接并 list/call 工具。
6. `AiServicesConfiguration` 装配 MCP Server Bean（Spring 生命周期管理）。
7. 单元测试：schema 生成、bridge 适配、MCP 往返（mock transport，不打真网络）。
8. `mvn clean test` 全量回归（Day 1/3/4/7/8）。
9. 更新 checklist.md + 证据。

## Technical Notes
- LangChain4j: 1.20.0（含 langchain4j-mcp 模块）
- LangGraph4j: 1.8.26
- Spring Boot: 4.1.1（webflux）
- Java: 17
- 传输：HTTP/SSE（不选 stdio，避免与 webflux stdin/stdout 冲突）

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
