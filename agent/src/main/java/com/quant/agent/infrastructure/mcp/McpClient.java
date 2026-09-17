package com.quant.agent.infrastructure.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.agent.infrastructure.mcp.protocol.JsonRpcRequest;
import com.quant.agent.infrastructure.mcp.protocol.JsonRpcResponse;
import com.quant.agent.infrastructure.mcp.protocol.McpProtocol;
import com.quant.agent.infrastructure.mcp.protocol.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ============================================================================================
// 【Day 9 · 阅读入口】McpClient —— Agent 作为 MCP Client，消费远端 MCP Server 的工具。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：MCP 的"客户端"。让本 Agent 能发现并调用外部 MCP Server 的工具。
//   学完能回答：
//     1. McpClient 和 McpServer 是什么关系？
//     2. 为什么 listTools 返回的是 Map 而不是 List？
//     3. callTool 的返回为什么要区分 isError？
//
//   💡 McpClient 和 McpServer 的关系：
//     一体两面：
//       - McpServer：把我们自己的 StockTools 暴露出去（供别人调）
//       - McpClient：调别人的 MCP Server（消费外部工具）
//     两者用同一套 MCP 协议（JsonRpc 信封 + MCP 业务消息）。
//
//   💡 为什么 listTools 返回 Map<String, ToolDefinition>？
//     调用时按工具名路由（tools/call 传 name），Map 比 List 查找快。
//     也方便调用方"有没有某个工具"的判断。
//
//   💡 callTool 为什么要区分 isError？
//     MCP 协议：业务失败走 result.isError=true，不走 JsonRpcError。
//     callTool 返回的是 result.content 的文本；isError=true 表示业务层失败（如股票不存在），
//     调用方需要据此决定是重试还是降级。
//
//   💡 为什么用 WebClient（非 RestTemplate）？
//     项目用的是 spring-boot-starter-webflux（reactive），WebClient 是它的原生客户端。
//     保持技术栈一致，不引入阻塞式 RestTemplate。
// ============================================================================================

/**
 * MCP 客户端：连接远端 MCP Server，发现并调用其工具。
 *
 * <p>使用 webflux {@link WebClient} 做非阻塞 HTTP 调用。
 */
public class McpClient {

    private static final Logger log = LoggerFactory.getLogger(McpClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String serverUrl;
    private volatile boolean initialized = false;
    private int requestCounter = 0;

    /**
     * @param serverUrl MCP Server 的基础 URL（如 http://localhost:8080）
     */
    public McpClient(String serverUrl, ObjectMapper objectMapper) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(this.serverUrl)
                .build();
    }

    // ====================================================================
    // 生命周期：initialize 握手
    // ====================================================================

    /**
     * 与 MCP Server 握手（initialize + initialized 通知）。
     *
     * <p>必须在 listTools / callTool 之前调用。
     */
    public synchronized void initialize() {
        if (initialized) {
            return;
        }

        Map<String, Object> initParams = new HashMap<>();
        initParams.put("protocolVersion", McpProtocol.PROTOCOL_VERSION);
        initParams.put("capabilities", Map.of("tools", Map.of()));
        initParams.put("clientInfo", Map.of("name", "lianghua-agent-client", "version", "1.0.0"));

        JsonRpcResponse response = call(McpProtocol.METHOD_INITIALIZE, initParams);
        if (response.error() != null) {
            throw new IllegalStateException("MCP initialize 失败: " + response.error().message());
        }
        log.info("MCP Client 初始化完成: server={}", serverUrl);

        // 发送 initialized 通知（无需等待响应）
        notify(McpProtocol.METHOD_INITIALIZED, Map.of());
        initialized = true;
    }

    // ====================================================================
    // tools/list —— 发现远端工具
    // ====================================================================

    /**
     * 获取远端所有工具定义。
     *
     * @return 工具名 → 工具定义的映射
     */
    public Map<String, ToolDefinition> listTools() {
        ensureInitialized();
        JsonRpcResponse response = call(McpProtocol.METHOD_TOOLS_LIST, Map.of());
        if (response.error() != null) {
            throw new IllegalStateException("tools/list 失败: " + response.error().message());
        }

        JsonNode toolsNode = response.result().path("tools");
        Map<String, ToolDefinition> tools = new HashMap<>();
        if (toolsNode.isArray()) {
            for (JsonNode toolNode : toolsNode) {
                try {
                    ToolDefinition def = objectMapper.treeToValue(toolNode, ToolDefinition.class);
                    tools.put(def.name(), def);
                } catch (JsonProcessingException e) {
                    log.warn("解析工具定义失败: {}", toolNode);
                }
            }
        }
        log.debug("MCP 发现远端工具: count={}", tools.size());
        return tools;
    }

    // ====================================================================
    // tools/call —— 调用远端工具
    // ====================================================================

    /**
     * 调用远端工具。
     *
     * @param name      工具名
     * @param arguments 参数（Map → 序列化为 JSON 对象）
     * @return 工具返回的文本结果
     * @throws McpToolException 业务层失败（result.isError=true）
     */
    public String callTool(String name, Map<String, Object> arguments) {
        return callToolInternal(name, objectMapper.valueToTree(arguments));
    }

    /**
     * 调用远端工具（JsonNode 参数版本）。
     */
    public String callTool(String name, JsonNode arguments) {
        return callToolInternal(name, arguments);
    }

    private String callToolInternal(String name, JsonNode arguments) {
        ensureInitialized();
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        params.put("arguments", arguments);

        JsonRpcResponse response = call(McpProtocol.METHOD_TOOLS_CALL, params);
        if (response.error() != null) {
            throw new IllegalStateException("tools/call 协议错误: " + response.error().message());
        }

        JsonNode content = response.result().path("content");
        boolean isError = response.result().path("isError").asBoolean(false);

        String text = extractText(content);
        if (isError) {
            throw new McpToolException(name, text);
        }
        return text;
    }

    // ====================================================================
    // 底层：JSON-RPC 调用
    // ====================================================================

    private synchronized String nextId() {
        return String.valueOf(++requestCounter);
    }

    private JsonRpcResponse call(String method, Map<String, Object> params) {
        String id = nextId();
        JsonRpcRequest request = JsonRpcRequest.of(id, method, objectMapper.valueToTree(params));
        // 自行序列化：避免 WebClient 编码器对 record 内的 JsonNode 字段调用 toString() 而非写出 JSON
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 MCP 请求失败", e);
        }

        String responseJson = webClient.post()
                .uri("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(requestJson)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            return objectMapper.readValue(responseJson, JsonRpcResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("解析 MCP 响应失败: " + responseJson, e);
        }
    }

    private void notify(String method, Map<String, Object> params) {
        JsonRpcRequest request = JsonRpcRequest.of(null, method, objectMapper.valueToTree(params));
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            log.warn("序列化 MCP 通知失败: method={}", method);
            return;
        }
        webClient.post()
                .uri("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestJson)
                .retrieve()
                .toBodilessEntity()
                .onErrorResume(e -> {
                    log.warn("MCP 通知发送失败: method={}, error={}", method, e.getMessage());
                    return Mono.empty();
                })
                .block();
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("MCP Client 未初始化；请先调用 initialize()");
        }
    }

    private String extractText(JsonNode content) {
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(block.path("text").asText());
                }
            }
            return sb.toString();
        }
        return content.toString();
    }

    // ====================================================================
    // 业务异常
    // ====================================================================

    /** MCP 工具业务层失败（result.isError=true）。 */
    public static class McpToolException extends RuntimeException {
        private final String toolName;

        public McpToolException(String toolName, String message) {
            super("MCP tool '" + toolName + "' failed: " + message);
            this.toolName = toolName;
        }

        public String getToolName() {
            return toolName;
        }
    }
}
