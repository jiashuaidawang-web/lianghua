package com.quant.agent.interfaces.http;

import com.quant.agent.infrastructure.llm.QuantLlmService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

// ============================================================================================
// 【Day 1 · 阅读入口】LlmStreamController —— Day 1 的"浏览器入口"，最薄的传输层。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 1 调用链的"最上游"。
//     浏览器 → LlmStreamController → QuantLlmService → StreamingChatModel → LLM 服务商
//   建议阅读时机：Day 1 最后读它（它只是水龙头，真正的水泵在 QuantLlmService）。
//   学完能回答：Spring WebFlux 怎么把 Flux 变成浏览器的 SSE 流？
//
//   💡 关键两件套：produces = TEXT_EVENT_STREAM_VALUE + 返回 Flux
//     - MediaType.TEXT_EVENT_STREAM_VALUE = "text/event-stream"，HTTP 的 SSE 协议标识。
//     - 返回 Flux<String>：WebFlux 框架会自动订阅这个 Flux，
//       每来一个元素就往 HTTP 响应体里写一段 "data: xxx\n\n"。
//     - 浏览器用 EventSource API 就能一段一段实时收到。
//
//   ⚠ 为什么是 GET 不是 POST？
//     SSE 协议浏览器端只能用 GET（EventSource 不支持 POST body）。
//     所以 prompt 拼在 URL 参数里。prompt 很长时要注意 URL 长度限制（~2KB）。
//
//   调用链位置（Day 1 完整链路）：
//     浏览器 EventSource("/api/v1/llm/stream?prompt=分析茅台")
//       → LlmStreamController.stream()
//         → QuantLlmService.streamChat(prompt) 返回 Flux
//           → Flux.create 订阅 → 调 StreamingChatModel.chat(prompt, handler)
//             → LLM 服务商逐段返回
//               → handler.onPartialResponse → sink.next
//                 → WebFlux 写 "data: 你\n\ndata: 好\n\n" → 浏览器实时显示
//
//   ⬇ Day 1 到这里结束。下一步进入 Day 2：看 StockAnalysis（输出契约 DTO）。
// ============================================================================================

@RestController
public class LlmStreamController {

    private final QuantLlmService service;

    public LlmStreamController(QuantLlmService service) {
        this.service = service;
    }

    // produces = TEXT_EVENT_STREAM_VALUE：响应头 Content-Type: text/event-stream
    // 浏览器看到这个头，就知道这是 SSE 流，自动用 EventSource 处理
    @GetMapping(value = "/api/v1/llm/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam String prompt) {
        // 直接把 Flux 返回给 WebFlux 框架，框架负责订阅 + 写出
        return service.streamChat(prompt);
    }
}
