package com.quant.agent.infrastructure.mcp.protocol;

/** Server 声明支持的能力（本期只支持 tools）。 */
public record ServerCapabilities(boolean tools) {

    public static ServerCapabilities withTools() {
        return new ServerCapabilities(true);
    }
}
