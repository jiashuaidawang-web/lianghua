package com.quant.agent.infrastructure.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.quant.agent.infrastructure.mcp.protocol.ToolDefinition;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

// ============================================================================================
// 【Day 9 · 阅读入口】McpToolBridge —— Day 9 的"复用核心"：把现有 @Tool 桥接为 MCP 工具。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：夹在 McpServer 和 StockTools 之间，做"协议适配"。
//   建议阅读时机：读完 McpServer + ToolExecutor 后读它。
//   学完能回答：
//     1. McpToolBridge 是怎么"零复制"复用 StockTools 的？
//     2. inputSchema（JSON Schema）是怎么从 @Tool/@P 反射生成的？
//     3. 调用时 arguments 是怎么从 JSON 转成 Java 方法参数的？
//
//   💡 "零复制"是什么意思？
//     McpToolBridge 不重写 getStockPrice / getFundamental 的业务逻辑。
//     它做的是：
//       1. 反射读取 @Tool/@P 注解 → 生成 JSON Schema（描述）
//       2. 持有 StockTools 的引用 → 调用时通过反射 invoke 原方法
//     业务逻辑 100% 复用 Day 8 的 StockTools → MarketDataGateway → EastMoney。
//
//   💡 inputSchema 生成过程（反射 → JSON Schema）：
//
//     对每个 @Tool 方法：
//       name        = 方法名（getStockPrice）
//       description = @Tool("...") 的值
//       inputSchema = {
//                       "type": "object",
//                       "properties": {
//                         "symbol": { "type": "string", "description": "@P 的值" }
//                       },
//                       "required": ["symbol"]
//                     }
//
//     反射 Parameter + @P 注解 → 拼成上面的 JSON Schema 树。
//
//   💡 调用时的参数转换（JSON → Java）：
//     tools/call 传 {"symbol":"600519"} → 反射拿到方法参数名"symbol" →
//     arguments.get("symbol").asText() → 传给 method.invoke(stockTools, "600519")
//
//   💡 为什么需要 -parameters 编译选项？
//     Parameter.getName() 默认返回 "arg0"（不是真名）。
//     开了 -parameters 后返回真实参数名 "symbol"，才能正确做 JSON→Java 映射。
//     pom.xml 已加 <maven.compiler.parameters>true</maven.compiler.parameters>。
//
//   ⬇ 下一步：看 McpClient（Agent 作为 MCP Client 消费远端工具）。
// ============================================================================================

/**
 * 把现有 {@link StockTools} 的 {@code @Tool} 方法桥接为 MCP 工具。
 *
 * <p>核心职责：
 * <ul>
 *   <li>反射扫描 @Tool 方法 → 生成 {@link ToolDefinition}（含 JSON Schema）</li>
 *   <li>生成 {@link ToolExecutor}（通过反射 invoke 原方法，零逻辑复制）</li>
 * </ul>
 */
public class McpToolBridge {

    private static final Logger log = LoggerFactory.getLogger(McpToolBridge.class);

    private final Object target;
    private final ObjectMapper objectMapper;

    /**
     * @param target       含 @Tool 方法的对象（通常是 StockTools，但可以是任意对象 —— 反射扫描其类）
     * @param objectMapper JSON 映射器
     */
    public McpToolBridge(Object target, ObjectMapper objectMapper) {
        this.target = target;
        this.objectMapper = objectMapper;
    }

    // ====================================================================
    // 扫描 StockTools，生成所有 MCP 工具定义
    // ====================================================================

    /**
     * 扫描 StockTools 中所有 @Tool 方法，生成 MCP 工具定义列表。
     *
     * @return 工具定义列表（每个含 name / description / inputSchema + 执行器）
     */
    public List<McpToolBinding> buildToolBindings() {
        List<McpToolBinding> bindings = new ArrayList<>();

        for (Method method : target.getClass().getDeclaredMethods()) {
            Tool toolAnno = method.getAnnotation(Tool.class);
            if (toolAnno == null) {
                continue; // 非 @Tool 方法跳过
            }

            String name = method.getName();
            // @Tool.value() 返回 String[]，拼接为完整描述
            String description = String.join(" ", toolAnno.value());
            JsonNode inputSchema = buildInputSchema(method);
            ToolExecutor executor = buildExecutor(method);

            ToolDefinition definition = new ToolDefinition(name, description, inputSchema);
            bindings.add(new McpToolBinding(definition, executor));

            log.info("MCP bridge 绑定工具: name={}, params={}", name, inputSchema.path("required").size());
        }

        return bindings;
    }

    // ====================================================================
    // 构建 JSON Schema（从方法参数 + @P 注解反射）
    // ====================================================================

    /**
     * 为单个 @Tool 方法生成 inputSchema（JSON Schema）。
     *
     * <p>格式：
     * <pre>
     * {
     *   "type": "object",
     *   "properties": { "symbol": { "type": "string", "description": "..." } },
     *   "required": ["symbol"]     * }
     * </pre>
     */
    private JsonNode buildInputSchema(Method method) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        List<String> required = new ArrayList<>();

        for (Parameter param : method.getParameters()) {
            String paramName = param.getName(); // 需要 -parameters 编译选项
            P pAnno = param.getAnnotation(P.class);

            ObjectNode prop = properties.putObject(paramName);
            prop.put("type", jsonTypeFor(param.getType()));
            if (pAnno != null) {
                prop.put("description", pAnno.value());
            }

            // 本期所有参数都视为 required（简化）
            required.add(paramName);
        }

        schema.set("properties", properties);
        schema.set("required", objectMapper.valueToTree(required));
        return schema;
    }

    /**
     * Java 类型 → JSON Schema type 的映射。
     */
    private String jsonTypeFor(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == long.class || type == Integer.class || type == Long.class) return "integer";
        if (type == double.class || type == float.class || type == Double.class || type == Float.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        return "string"; // 兜底
    }

    // ====================================================================
    // 构建 ToolExecutor（反射 invoke 原方法）
    // ====================================================================

    /**
     * 为单个 @Tool 方法生成 ToolExecutor。     *
     * <p>执行时：从 arguments JSON 中按参数名取值 → 转成 Java 类型 → method.invoke。
     */
    private ToolExecutor buildExecutor(Method method) {
        return arguments -> {
            Parameter[] params = method.getParameters();
            Object[] args = new Object[params.length];

            for (int i = 0; i < params.length; i++) {
                String paramName = params[i].getName();
                JsonNode valueNode = (arguments != null) ? arguments.get(paramName) : null;
                args[i] = convertArgument(valueNode, params[i].getType());
            }

            // 反射调用原方法（业务逻辑 100% 复用 StockTools）
            Object result = method.invoke(target, args);
            return result != null ? result.toString() : "";
        };
    }

    /**
     * 把 JSON 节点转成目标 Java 类型。
     */
    private Object convertArgument(JsonNode node, Class<?> targetType) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return defaultValue(targetType);
        }
        if (targetType == String.class) {
            return node.isTextual() ? node.asText() : node.toString();
        }
        if (targetType == int.class || targetType == Integer.class) {
            return node.isNumber() ? node.asInt() : Integer.parseInt(node.asText());
        }
        if (targetType == long.class || targetType == Long.class) {
            return node.isNumber() ? node.asLong() : Long.parseLong(node.asText());
        }
        if (targetType == double.class || targetType == Double.class) {
            return node.isNumber() ? node.asDouble() : Double.parseDouble(node.asText());
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return node.isBoolean() ? node.asBoolean() : Boolean.parseBoolean(node.asText());
        }
        // 兜底：用 Jackson 转换
        return objectMapper.convertValue(node, targetType);
    }

    private Object defaultValue(Class<?> targetType) {
        if (targetType == int.class) return 0;
        if (targetType == long.class) return 0L;
        if (targetType == double.class) return 0.0;
        if (targetType == boolean.class) return false;
        return null;
    }

    // ====================================================================
    // 内部包装：工具定义 + 执行器
    // ====================================================================

    /** 工具定义 + 执行器的绑定。 */
    public record McpToolBinding(ToolDefinition definition, ToolExecutor executor) {}
}
