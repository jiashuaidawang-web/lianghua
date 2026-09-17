package com.quant.agent.infrastructure.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.agent.infrastructure.mcp.protocol.CallToolResult;
import com.quant.agent.infrastructure.mcp.protocol.InitializeResult;
import com.quant.agent.infrastructure.mcp.protocol.ImplementationInfo;
import com.quant.agent.infrastructure.mcp.protocol.JsonRpcError;
import com.quant.agent.infrastructure.mcp.protocol.JsonRpcRequest;
import com.quant.agent.infrastructure.mcp.protocol.JsonRpcResponse;
import com.quant.agent.infrastructure.mcp.protocol.ListToolsResult;
import com.quant.agent.infrastructure.mcp.protocol.McpProtocol;
import com.quant.agent.infrastructure.mcp.protocol.ServerCapabilities;
import com.quant.agent.infrastructure.mcp.protocol.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.quant.agent.infrastructure.mcp.protocol.JsonRpc.INVALID_PARAMS;
import static com.quant.agent.infrastructure.mcp.protocol.JsonRpc.INVALID_REQUEST;
import static com.quant.agent.infrastructure.mcp.protocol.JsonRpc.INTERNAL_ERROR;
import static com.quant.agent.infrastructure.mcp.protocol.JsonRpc.METHOD_NOT_FOUND;
import static com.quant.agent.infrastructure.mcp.protocol.JsonRpc.NOT_INITIALIZED;
import static com.quant.agent.infrastructure.mcp.protocol.JsonRpc.PARSE_ERROR;

// ============================================================================================
// 【Day 9 · 阅读入口】McpServer —— MCP 协议的"业务核心"，纯逻辑、无网络、可单测。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：MCP Server 的心脏。处理 initialize / tools/list / tools/call。
//   建议阅读时机：读完 McpProtocol 后读它。
//   学完能回答：
//     1. 为什么 McpServer 不依赖 Spring / webflux？
//     2. "未初始化"检查是怎么实现的？为什么重要？
//     3. tool 业务失败和协议错误在返回时有什么不同？
//
//   💡 为什么 McpServer 不依赖 Spring / webflux？
//     核心协议逻辑和网络传输解耦：
//       - McpServer 只处理"给一个 JSON-RPC 请求 → 返回一个 JSON-RPC 响应"
//       - 传输（HTTP/SSE/stdio）由上层适配（McpController 负责 webflux HTTP）
//     好处：McpServer 可以脱离 Spring 上下文做纯单测；换传输方式不用改它。
//
//   💡 "未初始化"检查：
//     MCP 规范要求：Client 必须先调 initialize，才能调 tools/list / tools/call。
//     我们用 AtomicBoolean initialized 标志位跟踪。
//     未初始化就调 tools/* → 返回 JsonRpcError(NOT_INITIALIZED)。
//
//   💡 tool 业务失败 vs 协议错误：
//     - 协议错误（方法不存在、未初始化、参数缺失）→ JsonRpcError（信封层）
//     - 业务错误（股票代码非法、API 超时）→ result.isError = true（业务层）
//     这是 MCP 的明确区分：业务失败不等于协议失败。
//
//   💡 工具执行怎么路由？
//     tools/call 传 {name, arguments} → 从 toolRegistry 找 name → 调 executor.execute(arguments)。
//     executor 是 McpToolBridge 注入的，最终调到 StockTools → MarketDataGateway。
//
//   ⬇ 下一步：看 McpToolBridge（工具注册 + schema 生成 + 执行路由）。
// ============================================================================================

/**
 * MCP Server 核心逻辑：处理 JSON-RPC 请求，路由到对应 MCP 方法。
 *
 * <p>纯 POJO，不依赖 Spring / webflux。传输层由 {@code McpController} 适配。
 */
public class McpServer {

    private static final Logger log = LoggerFactory.getLogger(McpServer.class);

    private final ObjectMapper objectMapper;
    private final Map<String, McpToolExecutor> toolRegistry = new HashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /** Server 自身信息（initialize 时返回给 Client）。 */
    private final ImplementationInfo serverInfo;

    public McpServer(ObjectMapper objectMapper, ImplementationInfo serverInfo) {
        this.objectMapper = objectMapper;
        this.serverInfo = serverInfo;
    }

    // ====================================================================
    // 工具注册（由 McpToolBridge 调用，把现有 @Tool 方法注册进来）
    // ====================================================================

    /**
     * 注册一个 MCP 工具。
     *
     * @param definition 工具定义（name / description / inputSchema）
     * @param executor   工具执行器（收到 arguments → 返回文本结果）
     */
    public void registerTool(ToolDefinition definition, ToolExecutor executor) {
        String name = definition.name();
        if (toolRegistry.containsKey(name)) {
            log.warn("MCP 工具重复注册，覆盖: name={}", name);
        }
        toolRegistry.put(name, new McpToolExecutor(definition, executor));
        log.info("MCP 工具已注册: name={}, description={}", name, definition.description());
    }

    // ====================================================================
    // 核心入口：处理一条 JSON-RPC 请求，返回响应
    // ====================================================================
    // 为什么返回 String 而不是对象？
    //   因为传输层需要的是序列化后的 JSON 字符串。这里直接返回 String，
    //   让 Controller 原样写回 HTTP 响应，避免二次序列化/反序列化。

    /**
     * 处理一条 JSON-RPC 请求。
     *
     * @param requestJson 请求 JSON 字符串
     * @return 响应 JSON 字符串；如果是通知(notification)则返回 null
     */
    public String handle(String requestJson) {
        // ---- 解析请求 ----
        JsonRpcRequest request;
        try {
            request = objectMapper.readValue(requestJson, JsonRpcRequest.class);
        } catch (JsonProcessingException e) {
            log.warn("MCP 请求解析失败: {}", e.getMessage());
            return serialize(JsonRpcResponse.error(null, PARSE_ERROR, "Parse error: " + e.getMessage()));
        }

        // ---- 路由到具体方法 ----
        log.debug("MCP 请求: id={}, method={}", request.id(), request.method());
        JsonRpcResponse response = route(request);

        // ---- 通知(notification)无 id → 不回响应 ----
        if (response == null) {
            return null;
        }
        return serialize(response);
    }

    /**
     * 方法路由。
     *
     * <p>通知(无 id 的方法，如 "initialized")返回 null（表示无需响应）。
     */
    private JsonRpcResponse route(JsonRpcRequest request) {
        if (request.method() == null) {
            return JsonRpcResponse.error(request.id(), INVALID_REQUEST, "Missing method");
        }
        return switch (request.method()) {
            case McpProtocol.METHOD_INITIALIZED -> {
                // initialized 是通知：标记握手完成，但无需响应
                initialized.set(true);
                yield null;
            }
            case McpProtocol.METHOD_INITIALIZE -> handleInitialize(request);
            case McpProtocol.METHOD_TOOLS_LIST -> handleToolsList(request);
            case McpProtocol.METHOD_TOOLS_CALL -> handleToolsCall(request);
            default -> JsonRpcResponse.error(request.id(), METHOD_NOT_FOUND,
                    "Method not found: " + request.method());
        };
    }

    // ====================================================================
    // initialize —— 握手
    // ====================================================================
    // Client 发送：{ protocolVersion, capabilities, clientInfo }
    // Server 返回：{ protocolVersion, capabilities:{tools:true}, serverInfo }
    // 同时把 initialized 标志置 true（简化版：initialize 即视为已初始化，initialized 通知可选）。

    private JsonRpcResponse handleInitialize(JsonRpcRequest request) {
        // 解析 params（忽略 client 声明的 capabilities，我们固定支持 tools）
        initialized.set(true);
        log.info("MCP 初始化完成: client={}",
                request.params() != null ? request.params().path("clientInfo").path("name").asText("unknown") : "unknown");

        InitializeResult result = new InitializeResult(
                McpProtocol.PROTOCOL_VERSION,
                ServerCapabilities.withTools(),
                serverInfo);

        return JsonRpcResponse.success(request.id(), objectMapper.valueToTree(result));
    }

    // ====================================================================
    // tools/list —— 工具发现
    // ====================================================================
    // 必须已初始化，否则返回 NOT_INITIALIZED 错误。
    // 返回所有已注册工具的 name / description / inputSchema。

    private JsonRpcResponse handleToolsList(JsonRpcRequest request) {
        if (!initialized.get()) {
            return JsonRpcResponse.error(request.id(), NOT_INITIALIZED,
                    "Server not initialized; call 'initialize' first");
        }

        List<ToolDefinition> tools = toolRegistry.values().stream()
                .map(McpToolExecutor::definition)
                .toList();

        log.debug("MCP tools/list: 返回 {} 个工具", tools.size());
        return JsonRpcResponse.success(request.id(), objectMapper.valueToTree(new ListToolsResult(tools)));
    }

    // ====================================================================
    // tools/call —— 工具调用
    // ====================================================================
    // 必须已初始化。
    // 从 toolRegistry 找 name → 调 executor.execute(arguments) → 包装成 CallToolResult。
    // 工具不存在 → result.isError=true（业务错误，不是协议错误）。
    // 执行异常 → result.isError=true。

    private JsonRpcResponse handleToolsCall(JsonRpcRequest request) {
        if (!initialized.get()) {
            return JsonRpcResponse.error(request.id(), NOT_INITIALIZED,
                    "Server not initialized; call 'initialize' first");
        }

        // ---- 解析 params ----
        JsonNode params = request.params();
        if (params == null || !params.has("name")) {
            return JsonRpcResponse.error(request.id(), INVALID_PARAMS, "Missing required param: name");
        }
        String name = params.path("name").asText();
        JsonNode arguments = params.path("arguments");

        // ---- 找工具 ----
        McpToolExecutor executor = toolRegistry.get(name);
        if (executor == null) {
            log.warn("MCP 调用未注册工具: name={}", name);
            return JsonRpcResponse.success(request.id(),
                    objectMapper.valueToTree(CallToolResult.error("Unknown tool: " + name)));
        }

        // ---- 执行 ----
        try {
            String resultText = executor.executor().execute(arguments);
            return JsonRpcResponse.success(request.id(),
                    objectMapper.valueToTree(CallToolResult.success(resultText)));
        } catch (Exception e) {
            log.warn("MCP 工具执行异常: name={}, error={}", name, e.getMessage());
            return JsonRpcResponse.success(request.id(),
                    objectMapper.valueToTree(CallToolResult.error("Tool execution failed: " + e.getMessage())));
        }
    }

    // ====================================================================
    // 序列化
    // ====================================================================

    private String serialize(JsonRpcResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            // 响应序列化几乎不应该失败；兜底返回内部错误
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
        }
    }

    // ====================================================================
    // 包可见：给测试用
    // ====================================================================

    boolean isInitialized() {
        return initialized.get();
    }

    int toolCount() {
        return toolRegistry.size();
    }

    // ====================================================================
    // 内部包装：工具定义 + 执行器
    // ====================================================================

    private record McpToolExecutor(ToolDefinition definition, ToolExecutor executor) {}
}
