package com.quant.agent.infrastructure.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.agent.infrastructure.mcp.McpToolBridge.McpToolBinding;
import com.quant.agent.infrastructure.mcp.protocol.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// ============================================================================================
// 【Day 9 · 测试入口】McpToolBridgeTest —— 工具桥接测试：schema 生成 + 零复制复用。
// --------------------------------------------------------------------------------------------
//   测试策略：用一个独立的假 @Tool 类（不继承 StockTools，避免继承到未覆盖的方法），
//   验证 bridge 通过反射正确生成 schema 并路由调用。
//   验证：
//     1. 发现所有 @Tool 方法
//     2. name / description / inputSchema 正确生成
//     3. 调用时 arguments → Java 参数 正确转换
// ============================================================================================

class McpToolBridgeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** 独立的假 @Tool 类，只声明要测试的方法，避免继承带来的干扰。 */
    public static class FakeTools {
        @dev.langchain4j.agent.tool.Tool("获取价格")
        public String getStockPrice(@dev.langchain4j.agent.tool.P("股票代码，例如 600519") String symbol) {
            return "price-of-" + symbol;
        }

        @dev.langchain4j.agent.tool.Tool("获取基本面")
        public String getFundamental(@dev.langchain4j.agent.tool.P("股票代码") String symbol) {
            return "fundamental-of-" + symbol;
        }
    }

    @Test
    void buildToolBindings_discoversAllToolMethods() {
        McpToolBridge bridge = new McpToolBridge(new FakeTools(), mapper);
        List<McpToolBinding> bindings = bridge.buildToolBindings();

        assertEquals(2, bindings.size());
        assertTrue(bindings.stream().anyMatch(b -> b.definition().name().equals("getStockPrice")));
        assertTrue(bindings.stream().anyMatch(b -> b.definition().name().equals("getFundamental")));
    }

    @Test
    void inputSchema_hasSymbolPropertyAsRequired() {
        McpToolBridge bridge = new McpToolBridge(new FakeTools(), mapper);
        McpToolBinding binding = bridge.buildToolBindings().stream()
                .filter(b -> b.definition().name().equals("getStockPrice"))
                .findFirst().orElseThrow();
        ToolDefinition def = binding.definition();

        assertEquals("getStockPrice", def.name());
        assertEquals("获取价格", def.description());

        assertNotNull(def.inputSchema());
        assertEquals("object", def.inputSchema().path("type").asText());
        assertTrue(def.inputSchema().path("properties").path("symbol").isObject());
        assertEquals("string", def.inputSchema().path("properties").path("symbol").path("type").asText());
        assertEquals("股票代码，例如 600519",
                def.inputSchema().path("properties").path("symbol").path("description").asText());
        // symbol 在 required 里
        assertTrue(def.inputSchema().path("required").toString().contains("symbol"));
    }

    @Test
    void executor_invokesOriginalMethod_withConvertedArguments() throws Exception {
        McpToolBridge bridge = new McpToolBridge(new FakeTools(), mapper);
        McpToolBinding binding = bridge.buildToolBindings().stream()
                .filter(b -> b.definition().name().equals("getStockPrice"))
                .findFirst().orElseThrow();

        String result = binding.executor().execute(mapper.readTree("{\"symbol\":\"600519\"}"));
        assertEquals("price-of-600519", result);
    }

    @Test
    void executor_handlesNullArguments() throws Exception {
        McpToolBridge bridge = new McpToolBridge(new FakeTools(), mapper);
        McpToolBinding binding = bridge.buildToolBindings().stream()
                .filter(b -> b.definition().name().equals("getStockPrice"))
                .findFirst().orElseThrow();

        String result = binding.executor().execute(null);
        // symbol 为 null → String 默认 null → "price-of-null"
        assertEquals("price-of-null", result);
    }
}
