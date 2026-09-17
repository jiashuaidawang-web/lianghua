package com.quant.agent.interfaces.http;

import com.quant.agent.infrastructure.mcp.McpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

// ============================================================================================
// 【Day 9 · 阅读入口】McpController —— MCP Server 的 HTTP 传输层，挂在 /mcp。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：MCP Server 的"水龙头"（传输入口）。
//   建议阅读时机：读完 McpServer 后读它。
//   学完能回答：
//     1. 为什么 McpController 里没有业务逻辑？
//     2. 为什么用 Mono（reactive）？
//     3. 通知(notification)为什么返回 204 No Content？
//
//   💡 为什么没有业务逻辑？
//     传输层和业务层解耦：
//       - McpController 只做"接 HTTP → 转字符串 → 调 McpServer → 回 HTTP"
//       - 业务逻辑全在 McpServer（协议路由 + 工具调度）
//     换传输方式（如换成 SSE / stdio）只需换 Controller，McpServer 不动。
//
//   💡 为什么用 Mono？
//     项目用 spring-boot-starter-webflux（reactive stack），Controller 返回 Mono 是它的惯用写法。
//     McpServer.handle 是同步的（CPU 操作），包在 Mono.fromCallable 里适配 reactive。
//
//   💡 通知为什么返回 204？
//     JSON-RPC 通知(notification)无 id，Server 无需回响应。
//     McpServer.handle 返回 null → Controller 回 204 No Content。
//     这与 MCP 规范一致：initialized 等通知不需要响应体。
//
//   💡 请求格式（Streamable HTTP 传输）：
//     POST /mcp
//     Content-Type: application/json
//     { "jsonrpc":"2.0", "id":"1", "method":"tools/list", "params":{} }
//
//   ⬇ Day 9 工程代码到这里结束。下一步：看测试。
// ============================================================================================

/**
 * MCP Server 的 HTTP 端点（Streamable HTTP 传输）。
 *
 * <p>POST /mcp 接收 JSON-RPC 请求，委托 {@link McpServer} 处理。
 */
@RestController
@RequestMapping("/mcp")
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);

    private final McpServer mcpServer;

    public McpController(McpServer mcpServer) {
        this.mcpServer = mcpServer;
    }

    /**
     * 处理 MCP JSON-RPC 请求。
     *
     * <p>Streamable HTTP：一次 POST = 一条（或一批）JSON-RPC 请求。
     */
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> handle(@RequestBody String requestBody) {
        return Mono.fromCallable(() -> mcpServer.handle(requestBody))
                .map(response -> {
                    if (response == null) {
                        // 通知(notification)→ 无需响应体
                        return ResponseEntity.noContent().<String>build();
                    }
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(response);
                })
                .onErrorResume(e -> {
                    log.error("MCP 请求处理异常: {}", e.getMessage());
                    String errorJson = "{\"jsonrpc\":\"2.0\",\"id\":null,"
                            + "\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
                    return Mono.just(ResponseEntity.internalServerError().body(errorJson));
                });
    }
}
