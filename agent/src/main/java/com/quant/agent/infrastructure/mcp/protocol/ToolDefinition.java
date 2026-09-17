package com.quant.agent.infrastructure.mcp.protocol;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具定义：MCP 协议中的工具描述。
 *
 * <p>inputSchema 是 JSON Schema 对象，描述工具入参。
 */
public record ToolDefinition(
        String name,
        String description,
        JsonNode inputSchema) {}
