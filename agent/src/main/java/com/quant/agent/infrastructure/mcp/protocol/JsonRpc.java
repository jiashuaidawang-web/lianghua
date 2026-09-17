package com.quant.agent.infrastructure.mcp.protocol;

// ============================================================================================
// 【Day 9 · 阅读入口】JsonRpc.java —— JSON-RPC 2.0 协议常量。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：MCP 协议的最底层常量。所有 MCP 消息都套这层信封。
//   学完能回答：
//     1. JSON-RPC 2.0 的请求/响应长什么样？
//     2. "通知"(notification)和"请求"(request)有什么区别？
//     3. 为什么 response 用 NON_NULL 序列化？
//
//   💡 JSON-RPC 2.0 信封：
//
//     请求（Client → Server）：
//       { "jsonrpc":"2.0", "id":"1", "method":"tools/list", "params":{...} }
//                                       ↑ id 必填，Server 必须回同一个 id
//
//     通知（Client → Server，不等回包）：
//       { "jsonrpc":"2.0", "method":"initialized" }
//                          ↑ 无 id → Server 不回 response
//
//     响应（Server → Client）：
//       { "jsonrpc":"2.0", "id":"1", "result":{...} }       ← 成功
//       { "jsonrpc":"2.0", "id":"1", "error":{...} }        ← 失败
//       result 和 error 互斥，不会同时出现。
//
//   💡 为什么 response 用 @JsonInclude(NON_NULL)？
//     JSON-RPC 规范要求 result 和 error 只能有一个。
//     如果两个字段都序列化，会出现 "result":null,"error":null → 违反规范。
//     NON_NULL 让缺失的那个不输出。
//
//   ⬇ 下一步：看 JsonRpcRequest / JsonRpcResponse / JsonRpcError（信封记录）。
// ============================================================================================

/** JSON-RPC 2.0 协议常量。 */
public final class JsonRpc {

    private JsonRpc() {}

    /** JSON-RPC 协议版本。 */
    public static final String VERSION = "2.0";

    // ---- 标准错误码 ----
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    // ---- MCP 自定义：Server 未初始化就收到 tools/list 等 ----
    public static final int NOT_INITIALIZED = -32002;
}
