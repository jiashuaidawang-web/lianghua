package com.quant.agent.infrastructure.mcp.protocol;

/** initialize 响应的 result。 */
public record InitializeResult(
        String protocolVersion,
        ServerCapabilities capabilities,
        ImplementationInfo serverInfo) {}
