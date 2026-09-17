package com.quant.agent.infrastructure.mcp.protocol;

/** 实现方信息（initialize 的 clientInfo / serverInfo 共用）。 */
public record ImplementationInfo(String name, String version) {}
