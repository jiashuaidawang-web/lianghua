package com.quant.agent.infrastructure.mcp.protocol;

// ============================================================================================
// 【Day 9 · 阅读入口】McpProtocol.java —— MCP 协议常量（协议版本 + 方法名）。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：MCP 协议的应用层常量。业务消息类型拆分到同包下的独立文件。
//   学完能回答：MCP 的 3 个核心方法（initialize / tools/list / tools/call）的入参出参各是什么？
//
//   💡 MCP 核心 3 方法：
//     1. initialize  → params:{protocolVersion,capabilities,clientInfo}  result:{protocolVersion,capabilities,serverInfo}
//     2. tools/list  → params:{cursor?}                                 result:{tools:[{name,description,inputSchema}]}
//     3. tools/call  → params:{name,arguments}                          result:{content:[{type,text}],isError}
//   💡 tool 业务失败走 result.isError=true（不是 JsonRpcError）；协议失败才走 JsonRpcError。
//
//   ⬇ 下一步：看 McpServer（处理这些消息的核心逻辑）。
// ============================================================================================

/** MCP 协议常量：协议版本 + 方法名。 */
public final class McpProtocol {

    private McpProtocol() {}

    /** 支持的 MCP 协议版本。 */
    public static final String PROTOCOL_VERSION = "2024-11-05";

    /** 核心方法名。 */
    public static final String METHOD_INITIALIZE = "initialize";
    public static final String METHOD_INITIALIZED = "initialized";
    public static final String METHOD_TOOLS_LIST = "tools/list";
    public static final String METHOD_TOOLS_CALL = "tools/call";
}
