package com.quant.agent.infrastructure.mcp.protocol;

/** 文本内容块（MCP content 数组的元素）。 */
public record TextContent(String type, String text) {

    public TextContent(String text) {
        this("text", text);
    }
}
