package com.quant.agent.infrastructure.mcp.protocol;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 请求信封。
 *
 * <p>id 必填（String）。通知(notification)用无 id 消息表示。
 */
public record JsonRpcRequest(
        String jsonrpc,
        String id,
        String method,
        JsonNode params) {

    /** 便捷构造器：jsonrpc 自动填 "2.0"。供 Java 代码使用（Jackson 反序列化走全参构造器）。 */
    public static JsonRpcRequest of(String id, String method, JsonNode params) {
        return new JsonRpcRequest(JsonRpc.VERSION, id, method, params);
    }
}
