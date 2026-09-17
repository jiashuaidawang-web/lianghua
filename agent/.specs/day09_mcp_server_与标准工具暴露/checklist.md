# Day 9 Checklist

- [x] Function works on the happy path.
- [x] Error path is covered.
- [x] State/DTO/Tool result is validated.
- [x] No real credentials committed.
- [x] No beta/snapshot dependency added（自研 MCP，零新依赖；撤回 langchain4j-mcp beta）。
- [x] Unit tests pass.
- [x] Manual verification completed where applicable.

## Test Results（实测证据）

```
Tests run: 93, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

新增测试（19 个，3 个测试类）：
- `McpServerTest` (11 tests) —— initialize 握手、tools/list 发现、tools/call 调用、未初始化拦截(-32002)、工具不存在(isError)、执行异常(isError)、参数缺失(-32602)、未知方法(-32601)、畸形 JSON(-32700)、通知无响应
- `McpToolBridgeTest` (4 tests) —— 发现 @Tool 方法、inputSchema 生成（symbol required + JSON Schema）、arguments→Java 转换、null 参数
- `McpEndToEndTest` (4 tests) —— 完整 MCP 会话（initialize→list→call 真实 HTTP 往返）、未知工具抛 McpToolException、未初始化抛 IllegalStateException、执行异常传播

核心改动（9 个新增 + 2 个修改）：
- 新增 `infrastructure/mcp/protocol/JsonRpc.java` —— JSON-RPC 2.0 常量 + 错误码
- 新增 `infrastructure/mcp/protocol/JsonRpcRequest.java` —— 请求信封（record）
- 新增 `infrastructure/mcp/protocol/JsonRpcResponse.java` —— 响应信封（record, NON_NULL）
- 新增 `infrastructure/mcp/protocol/JsonRpcError.java` —— 错误体（record）
- 新增 `infrastructure/mcp/protocol/McpProtocol.java` —— MCP 协议版本 + 方法名常量
- 新增 `infrastructure/mcp/protocol/ToolDefinition.java` —— 工具定义（record）
- 新增 `infrastructure/mcp/protocol/CallToolResult.java` —— tools/call 结果（record）
- 新增 `infrastructure/mcp/protocol/TextContent.java` —— 文本内容块（record）
- 新增 `infrastructure/mcp/protocol/ImplementationInfo.java` —— 实现方信息（record）
- 新增 `infrastructure/mcp/protocol/ServerCapabilities.java` —— Server 能力（record）
- 新增 `infrastructure/mcp/protocol/InitializeResult.java` —— initialize 结果（record）
- 新增 `infrastructure/mcp/protocol/ListToolsResult.java` —— tools/list 结果（record）
- 新增 `infrastructure/mcp/McpServer.java` —— MCP Server 核心逻辑（纯 POJO，无网络）
- 新增 `infrastructure/mcp/ToolExecutor.java` —— 工具执行器函数式接口
- 新增 `infrastructure/mcp/McpToolBridge.java` —— @Tool→MCP 桥接（反射 schema + 零复制执行）
- 新增 `infrastructure/mcp/McpClient.java` —— MCP Client（WebClient + 自序列化）
- 新增 `interfaces/http/McpController.java` —— /mcp webflux 端点
- 修改 `infrastructure/llm/AiServicesConfiguration.java` —— 装配 McpToolBridge + McpServer Bean
- 修改 `pom.xml` —— 加 `maven.compiler.parameters=true`（bridge 反射参数名）
- 修改 `constitution.md` —— 记录 MCP 自研决策（零 beta）

关键设计决策：
- **零 beta 依赖**：langchain4j-mcp 仅有 beta30 且只含 Client；撤回依赖，完全自研 Server+Client（webflux + JDK HttpServer 测试）
- **同 JVM 内嵌**：McpServer 作为 Spring Bean，与 webflux 共享进程；McpController 挂在 /mcp
- **业务逻辑零复制**：McpToolBridge 反射 StockTools 的 @Tool/@P 注解生成 JSON Schema，执行时 method.invoke 原方法 → 复用 Day 8 护栏链（限流/缓存/降级）
- **Server 纯逻辑可单测**：McpServer 不依赖 Spring/网络，直接 new 出来测
- **Client 自序列化**：WebClient 编码器对 record 内 JsonNode 字段会调 toString() 而非写 JSON → 客户端自行 writeValueAsString 后发 String body
- **-parameters 编译选项**：bridge 通过 Parameter.getName() 获取真实参数名（symbol）生成 JSON Schema 属性

踩过的坑（已修复）：
- `JsonRpcRequest` 同时有 3 参和 4 参构造器 → Jackson 反序列化 params 为空 → 改为静态工厂 `JsonRpcRequest.of()`
- WebClient `bodyValue(record)` 对 JsonNode 字段序列化异常 → 客户端手动 `writeValueAsString`
- 匿名内部类反射不可访问 → 提取为公共静态嵌套类 FakeStockTools

## Vertical Evolution Contract

### Capability
MCP Capability

### Before
Day 8 Tool 只通过本地 Tool registry 调用；外部 Host 无法发现/调用。

### After
同一工具能力可通过 MCP 标准协议被外部 Agent/Host 发现和调用（POST /mcp）；Agent 亦可作为 MCP Client 消费远端工具。业务逻辑零复制，护栏链统一生效。

### Reuse-first
复用 Day 1~8 的 StockTools、MarketDataGateway、护栏链、领域对象、测试基建。业务逻辑零复制（bridge 反射 invoke 原方法）。

### Regression
完成后验证 Day 1/3/4/7/8 核心能力不退化：93 tests 全绿，含全部历史测试（74 → 93，新增 19）。
