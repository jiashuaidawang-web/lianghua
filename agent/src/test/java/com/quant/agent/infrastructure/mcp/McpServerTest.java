package com.quant.agent.infrastructure.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.agent.infrastructure.mcp.protocol.ImplementationInfo;
import com.quant.agent.infrastructure.mcp.protocol.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// ============================================================================================
// 【Day 9 · 测试入口】McpServerTest —— MCP Server 核心逻辑的纯单元测试（无网络、无 Spring）。
// --------------------------------------------------------------------------------------------
//   测试策略：McpServer 是纯 POJO，直接 new 出来测，不启动 webflux。
//   覆盖：initialize 握手、tools/list 发现、tools/call 调用、未初始化拦截、工具不存在、执行异常。
// ============================================================================================

class McpServerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private McpServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new McpServer(mapper, new ImplementationInfo("lianghua-test", "1.0.0"));
        // 注册一个假工具：echo，入参 {text: string}，返回 "echo: <text>"
        ToolDefinition echoDef = new ToolDefinition("echo", "回显输入的文本",
                mapper.readTree("{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}"));
        server.registerTool(echoDef, args -> "echo: " + args.path("text").asText(""));
    }

    // -----------------------------------------------------------------
    // initialize 握手
    // -----------------------------------------------------------------

    @Test
    void initialize_returnsServerInfoAndSupportsTools() throws Exception {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"test-client\",\"version\":\"1.0\"}}}";

        String resp = server.handle(req);

        assertNotNull(resp);
        JsonNode node = mapper.readTree(resp);
        assertEquals("2.0", node.path("jsonrpc").asText());
        assertEquals("1", node.path("id").asText());
        // result 存在，error 不存在
        assertTrue(node.path("result").isObject());
        assertFalse(node.path("error").isObject());
        // serverInfo
        assertEquals("lianghua-test", node.path("result").path("serverInfo").path("name").asText());
        assertEquals("1.0.0", node.path("result").path("serverInfo").path("version").asText());
        // capabilities.tools = true
        assertTrue(node.path("result").path("capabilities").path("tools").asBoolean());
        // 协议版本
        assertEquals("2024-11-05", node.path("result").path("protocolVersion").asText());
    }

    @Test
    void initialized_isANotification_noResponse() throws Exception {
        // 先 initialize
        server.handle("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"c\",\"version\":\"1\"}}}");
        // initialized 是通知（无 id）→ 返回 null
        String resp = server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"initialized\"}");
        assertNull(resp);
    }

    // -----------------------------------------------------------------
    // tools/list —— 工具发现
    // -----------------------------------------------------------------

    @Test
    void toolsList_beforeInitialize_returnsNotInitializedError() throws Exception {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"method\":\"tools/list\",\"params\":{}}";
        String resp = server.handle(req);

        JsonNode node = mapper.readTree(resp);
        assertTrue(node.path("error").isObject());
        assertEquals(-32002, node.path("error").path("code").asInt());
    }

    @Test
    void toolsList_afterInitialize_returnsRegisteredTools() throws Exception {
        initializeServer();

        String req = "{\"jsonrpc\":\"2.0\",\"id\":\"3\",\"method\":\"tools/list\",\"params\":{}}";
        String resp = server.handle(req);

        JsonNode node = mapper.readTree(resp);
        assertFalse(node.path("error").isObject());
        JsonNode tools = node.path("result").path("tools");
        assertEquals(1, tools.size());
        assertEquals("echo", tools.get(0).path("name").asText());
        assertEquals("回显输入的文本", tools.get(0).path("description").asText());
        // inputSchema 是 JSON Schema
        assertNotNull(tools.get(0).path("inputSchema").path("properties").path("text"));
    }

    // -----------------------------------------------------------------
    // tools/call —— 工具调用
    // -----------------------------------------------------------------

    @Test
    void toolsCall_beforeInitialize_returnsNotInitializedError() throws Exception {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":\"4\",\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"echo\",\"arguments\":{\"text\":\"hi\"}}}";
        String resp = server.handle(req);

        JsonNode node = mapper.readTree(resp);
        assertEquals(-32002, node.path("error").path("code").asInt());
    }

    @Test
    void toolsCall_returnsToolResult() throws Exception {
        initializeServer();

        String req = "{\"jsonrpc\":\"2.0\",\"id\":\"5\",\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"echo\",\"arguments\":{\"text\":\"hello world\"}}}";
        String resp = server.handle(req);

        JsonNode node = mapper.readTree(resp);
        assertFalse(node.path("error").isObject());
        JsonNode content = node.path("result").path("content");
        assertEquals(1, content.size());
        assertEquals("text", content.get(0).path("type").asText());
        assertEquals("echo: hello world", content.get(0).path("text").asText());
        assertFalse(node.path("result").path("isError").asBoolean());
    }

    @Test
    void toolsCall_unknownTool_returnsIsErrorTrue() throws Exception {
        initializeServer();

        String req = "{\"jsonrpc\":\"2.0\",\"id\":\"6\",\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"nonexistent\",\"arguments\":{}}}";
        String resp = server.handle(req);

        JsonNode node = mapper.readTree(resp);
        // 业务错误走 result.isError=true，不是 JsonRpcError
        assertFalse(node.path("error").isObject());
        assertTrue(node.path("result").path("isError").asBoolean());
        assertTrue(node.path("result").path("content").get(0).path("text").asText().contains("Unknown tool"));
    }

    @Test
    void toolsCall_executorThrows_returnsIsErrorTrue() throws Exception {
        // 注册一个会抛异常的工具
        server.registerTool(
                new ToolDefinition("boom", "总是失败", mapper.readTree("{\"type\":\"object\",\"properties\":{}}")),
                args -> { throw new RuntimeException("kaboom"); });
        initializeServer();

        String req = "{\"jsonrpc\":\"2.0\",\"id\":\"7\",\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"boom\",\"arguments\":{}}}";
        String resp = server.handle(req);

        JsonNode node = mapper.readTree(resp);
        assertFalse(node.path("error").isObject());
        assertTrue(node.path("result").path("isError").asBoolean());
        assertTrue(node.path("result").path("content").get(0).path("text").asText().contains("kaboom"));
    }

    @Test
    void toolsCall_missingNameParam_returnsInvalidParams() throws Exception {
        initializeServer();

        String req = "{\"jsonrpc\":\"2.0\",\"id\":\"8\",\"method\":\"tools/call\",\"params\":{}}";
        String resp = server.handle(req);

        JsonNode node = mapper.readTree(resp);
        assertEquals(-32602, node.path("error").path("code").asInt());
    }

    // -----------------------------------------------------------------
    // 协议错误
    // -----------------------------------------------------------------

    @Test
    void unknownMethod_returnsMethodNotFound() throws Exception {
        initializeServer();
        String req = "{\"jsonrpc\":\"2.0\",\"id\":\"9\",\"method\":\"foo/bar\",\"params\":{}}";
        String resp = server.handle(req);

        JsonNode node = mapper.readTree(resp);
        assertEquals(-32601, node.path("error").path("code").asInt());
    }

    @Test
    void malformedJson_returnsParseError() throws Exception {
        String resp = server.handle("{this is not json");

        JsonNode node = mapper.readTree(resp);
        assertEquals(-32700, node.path("error").path("code").asInt());
    }

    // -----------------------------------------------------------------
    // 辅助
    // -----------------------------------------------------------------

    private void initializeServer() {
        server.handle("{\"jsonrpc\":\"2.0\",\"id\":\"init\",\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"c\",\"version\":\"1\"}}}");
    }
}
