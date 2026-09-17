package com.quant.agent.infrastructure.mcp.protocol;

import java.util.List;

/**
 * tools/call 响应的 result。
 *
 * <p>MCP 设计：业务失败走 {@code result.isError=true}，不走 JsonRpcError。
 * 只有协议层错误（方法不存在、未初始化）才走 JsonRpcError。
 */
public record CallToolResult(List<TextContent> content, boolean isError) {

    /** 成功结果的工厂。 */
    public static CallToolResult success(String text) {
        return new CallToolResult(List.of(new TextContent(text)), false);
    }

    /** 业务错误结果的工厂（注意：仍是 result，不是 JsonRpcError）。 */
    public static CallToolResult error(String text) {
        return new CallToolResult(List.of(new TextContent(text)), true);
    }
}
