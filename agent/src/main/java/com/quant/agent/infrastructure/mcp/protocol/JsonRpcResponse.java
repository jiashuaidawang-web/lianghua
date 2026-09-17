package com.quant.agent.infrastructure.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 响应信封。
 *
 * <p>result 和 error 互斥。{@code @JsonInclude(NON_NULL)} 确保序列化时只输出其中之一。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcResponse(
        String jsonrpc,
        String id,
        JsonNode result,
        JsonRpcError error) {

    public JsonRpcResponse(String id, JsonNode result) {
        this(JsonRpc.VERSION, id, result, null);
    }

    public JsonRpcResponse(String id, JsonRpcError error) {
        this(JsonRpc.VERSION, id, null, error);
    }

    /** 成功响应工厂。 */
    public static JsonRpcResponse success(String id, JsonNode result) {
        return new JsonRpcResponse(JsonRpc.VERSION, id, result, null);
    }

    /** 错误响应工厂。 */
    public static JsonRpcResponse error(String id, int code, String message) {
        return new JsonRpcResponse(JsonRpc.VERSION, id, null, new JsonRpcError(code, message, null));
    }
}
