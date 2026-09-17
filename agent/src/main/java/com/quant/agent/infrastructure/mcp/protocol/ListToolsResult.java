package com.quant.agent.infrastructure.mcp.protocol;

import java.util.List;

/** tools/list 响应的 result。 */
public record ListToolsResult(List<ToolDefinition> tools) {}
