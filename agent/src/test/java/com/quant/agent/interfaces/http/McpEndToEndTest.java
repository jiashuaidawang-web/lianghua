package com.quant.agent.interfaces.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.agent.application.tool.StockTools;
import com.quant.agent.infrastructure.mcp.McpClient;
import com.quant.agent.infrastructure.mcp.McpServer;
import com.quant.agent.infrastructure.mcp.McpToolBridge;
import com.quant.agent.infrastructure.mcp.protocol.ImplementationInfo;
import com.quant.agent.infrastructure.mcp.protocol.ToolDefinition;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// ============================================================================================
// 【Day 9 · 测试入口】McpEndToEndTest —— 端到端 HTTP 集成测试（JDK HttpServer + 真实 McpClient）。
// --------------------------------------------------------------------------------------------
//   测试策略：
//     - 用 JDK HttpServer 在本机随机端口启动一个 /mcp 端点（内部调 McpServer）。
//     - 用真实 McpClient 连接它，执行完整 MCP 会话。
//     - StockTools 用假实现（不打网络）。
//   这比 @WebFluxTest 更真实：测试了真正的 HTTP 序列化/反序列化往返。
//   覆盖：initialize 握手 → tools/list 发现 → tools/call 调用 → 业务错误路径。
// ============================================================================================

class McpEndToEndTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer httpServer;
    private McpClient client;
    private McpServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        // 构建 MCP Server（桥接假 StockTools）—— 用公共静态嵌套类，确保反射可访问
        StockTools fakeTools = new FakeStockTools();
        McpToolBridge bridge = new McpToolBridge(fakeTools, MAPPER);
        server = new McpServer(MAPPER, new ImplementationInfo("lianghua-test", "1.0.0"));
        bridge.buildToolBindings().forEach(b -> server.registerTool(b.definition(), b.executor()));

        // 启动 JDK HttpServer，/mcp 端点转发到 McpServer.handle
        httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        port = httpServer.getAddress().getPort();
        httpServer.createContext("/mcp", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            byte[] body = exchange.getRequestBody().readAllBytes();
            String response = server.handle(new String(body, StandardCharsets.UTF_8));
            if (response == null) {
                // 通知 → 204 No Content
                exchange.sendResponseHeaders(204, -1);
            } else {
                byte[] respBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, respBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(respBytes);
                }
            }
            exchange.close();
        });
        httpServer.start();

        // 真实 MCP Client
        client = new McpClient("http://localhost:" + port, MAPPER);
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    // -----------------------------------------------------------------
    // 完整 MCP 会话
    // -----------------------------------------------------------------

    @Test
    void fullSession_initialize_listTools_callTool() {
        // 1. initialize 握手
        client.initialize();
        assertNotNull(client.listTools());

        // 2. tools/list → 发现 getStockPrice（和 getFundamental，共 2 个）
        Map<String, ToolDefinition> tools = client.listTools();
        assertEquals(2, tools.size());
        assertTrue(tools.containsKey("getStockPrice"));
        ToolDefinition def = tools.get("getStockPrice");
        assertEquals("获取股票当前价格", def.description());
        // inputSchema 含 symbol 属性
        assertNotNull(def.inputSchema().path("properties").path("symbol"));
        assertTrue(def.inputSchema().path("required").toString().contains("symbol"));

        // 3. tools/call → 调 getStockPrice，返回假数据
        String result = client.callTool("getStockPrice", Map.of("symbol", "600519"));
        assertTrue(result.contains("600519"));
        assertTrue(result.contains("1500.0"));
        assertTrue(result.contains("\"test\":true"));
    }

    @Test
    void callTool_unknownTool_throwsMcpToolException() {
        client.initialize();

        McpClient.McpToolException ex = assertThrows(McpClient.McpToolException.class,
                () -> client.callTool("nonexistent", Map.of()));
        assertEquals("nonexistent", ex.getToolName());
    }

    @Test
    void listTools_beforeInitialize_throwsIllegalState() {
        assertThrows(IllegalStateException.class, () -> client.listTools());
    }

    // -----------------------------------------------------------------
    // 业务错误路径：工具抛异常 → Client 收到 McpToolException
    // -----------------------------------------------------------------

    @Test
    void callTool_executorThrows_propagatesAsMcpToolException() throws IOException {
        // 单独启动一个带"会抛异常工具"的 server
        HttpServer boomServerHandle = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        int boomPort = boomServerHandle.getAddress().getPort();
        McpServer boomServer = new McpServer(MAPPER, new ImplementationInfo("boom", "1.0"));
        boomServer.registerTool(
                new ToolDefinition("boom", "总是失败", MAPPER.readTree("{\"type\":\"object\",\"properties\":{}}")),
                args -> { throw new RuntimeException("kaboom"); });
        boomServerHandle.createContext("/mcp", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String response = boomServer.handle(new String(body, StandardCharsets.UTF_8));
            if (response == null) {
                exchange.sendResponseHeaders(204, -1);
            } else {
                byte[] respBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, respBytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(respBytes); }
            }
            exchange.close();
        });
        boomServerHandle.start();

        try {
            McpClient boomClient = new McpClient("http://localhost:" + boomPort, MAPPER);
            boomClient.initialize();
            McpClient.McpToolException ex = assertThrows(McpClient.McpToolException.class,
                    () -> boomClient.callTool("boom", Map.of()));
            assertTrue(ex.getMessage().contains("kaboom"));
        } finally {
            boomServerHandle.stop(0);
        }
    }

    /** 公共静态假 StockTools：返回确定性数据，不打网络。公共类确保反射可访问。 */
    public static class FakeStockTools extends StockTools {
        public FakeStockTools() {
            super(null);
        }

        @Override
        @dev.langchain4j.agent.tool.Tool("获取股票当前价格")
        public String getStockPrice(@dev.langchain4j.agent.tool.P("股票代码，例如 600519") String symbol) {
            return "{\"symbol\":\"" + symbol + "\",\"price\":1500.0,\"test\":true}";
        }

        @Override
        @dev.langchain4j.agent.tool.Tool("获取股票基本面信息")
        public String getFundamental(@dev.langchain4j.agent.tool.P("股票代码，例如 600519") String symbol) {
            return "{\"symbol\":\"" + symbol + "\",\"pe\":25.0,\"test\":true}";
        }
    }
}
