package com.quant.agent.infrastructure.mcp;

import com.fasterxml.jackson.databind.JsonNode;

// ============================================================================================
// 【Day 9 · 阅读入口】ToolExecutor —— MCP 工具的执行器接口。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：McpServer 和 McpToolBridge 之间的"执行契约"。
//   学完能回答：为什么 ToolExecutor 是函数式接口？
//
//   💡 为什么是函数式接口（@FunctionalInterface）？
//     每个工具最终都是"入参 JsonNode → 出参 String"的纯函数。
//     用函数式接口，McpToolBridge 可以用 lambda 把 StockTools 方法包装成 ToolExecutor，
//     无需为每个工具写一个实现类。
// ============================================================================================

/**
 * MCP 工具执行器：接收 JSON arguments，返回文本结果。
 *
 * <p>业务异常请直接抛出，{@link McpServer} 会捕获并包装成 {@code result.isError=true}。
 */
@FunctionalInterface
public interface ToolExecutor {

    /**
     * 执行工具。
     *
     * @param arguments JSON 对象节点（tools/call 的 params.arguments）
     * @return 文本结果（会包成 MCP content[type=text]）
     * @throws Exception 执行失败（会被 McpServer 转为 isError=true）
     */
    String execute(JsonNode arguments) throws Exception;
}
