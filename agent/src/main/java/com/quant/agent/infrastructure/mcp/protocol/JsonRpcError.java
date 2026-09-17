package com.quant.agent.infrastructure.mcp.protocol;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 错误体。
 *
 * <p>错误码遵循 JSON-RPC 标准：
 * <ul>
 *   <li>-32700 解析错误（Parse error）</li>
 *   <li>-32600 无效请求（Invalid Request）</li>
 *   <li>-32601 方法不存在（Method not found）</li>
 *   <li>-32602 参数无效（Invalid params）</li>
 *   <li>-32603 内部错误（Internal error）</li>
 *   <li>-32000..-32099 服务器自定义错误（我们用 -32002 表示"未初始化"）</li>
 * </ul>
 */
public record JsonRpcError(int code, String message, JsonNode data) {
}
