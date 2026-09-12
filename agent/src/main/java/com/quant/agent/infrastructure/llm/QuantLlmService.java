package com.quant.agent.infrastructure.llm;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

// ============================================================================================
// 【Day 1 · 阅读入口】QuantLlmService —— 把 LLM 的"异步回调"包装成 Reactor Flux 的适配器。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 1 的核心桥梁。
//      StreamingChatModel（LangChain4j，回调风格）
//        ←→ QuantLlmService（桥接）
//          ←→ Flux<String>（WebFlux 流式输出）
//   建议阅读时机：Day 1 读完 LlmConfiguration 后立刻读它。
//   学完能回答：
//     1. LLM 是"边生成边吐"的，怎么变成浏览器能一段一段收到的 SSE？
//     2. Flux.create + Sink 是什么模式？为什么不能直接 return Flux？
//
//   💡 核心难点：两种"流"的范式不匹配
//     - LangChain4j 的 StreamingChatModel 是"回调风格"：你传一个 Handler 进去，
//       它在后台调 onPartialResponse("你")、onPartialResponse("好")、onCompleteResponse(...)。
//     - Spring WebFlux 的 SSE 要求返回 Flux<String>：订阅一段吐一段。
//     - 两者不兼容！QuantLlmService 的工作就是把"回调"翻译成"Flux"。
//
//   💡 Flux.create(sink -> ...) 是什么模式？
//     这是 Reactor 的"桥接模式"（Bridge）：
//       - Flux.create() 创建一个"还没开始流的 Flux"（冷流，订阅才执行）。
//       - 你拿到一个 Sink（"下水道口"），随时 sink.next(一段文本) 就往 Flux 里吐一段。
//       - LLM 回调 onPartialResponse 时，你就 sink.next(token) —— 一段一段推到浏览器。
//       - LLM 回调 onCompleteResponse 时，你就 sink.complete() —— 告诉浏览器"结束了"。
//       - LLM 回调 onError 时，你就 sink.error(err) —— 告诉浏览器"出错了"。
//
//   ⚠ System.out.println 只是调试用的，生产应该用 log.info。
//
//   ⬇ 下一步：看 LlmStreamController，它调用这个 service 并返回给浏览器。
// ============================================================================================

@Service
public class QuantLlmService {

    // 注入的是 StreamingChatModel（流式），不是 ChatModel（一次性）
    private final StreamingChatModel model;

    public QuantLlmService(StreamingChatModel model) {
        this.model = model;
    }

    /**
     * 把 LLM 的流式输出转成 Flux<String>（浏览器 SSE 能吃的格式）。
     *
     * @param userPrompt 用户输入的问题
     * @return 一段一段文本的流（订阅后才开始真正调 LLM）
     */
    public Flux<String> streamChat(String userPrompt) {
        // Flux.create：创建一个"异步桥接"。参数是个 Lambda，拿到 sink 往里吐数据。
        return Flux.create(sink ->
                // 调 LangChain4j 的流式 chat：传 prompt + 一个回调 Handler
                model.chat(userPrompt, new StreamingChatResponseHandler() {

                    // LLM 每生成一小段（几分之一秒一次），就被调一次
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        // 拿到一小段就往 Flux 里吐一段 → 浏览器立刻收到这段 SSE
                        sink.next(partialResponse);
                    }

                    // LLM 整个回答完毕，被调一次（只调一次）
                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        // 完整文本（调试用，生产建议换成 log.info）
                        String text = completeResponse.aiMessage().text();
                        System.out.println("完整的文本:" + text);
                        // token 用量（计费关键指标）
                        TokenUsage tokenUsage = completeResponse.tokenUsage();
                        System.out.println("token 用量:" + tokenUsage);
                        // 为什么停：STOP=正常结束、LENGTH=超长截断、TOOL_CALLS=要调工具
                        FinishReason finishReason = completeResponse.finishReason();
                        System.out.println("为什么停:" + finishReason);
                        // 告诉订阅者"流结束了" → 浏览器关闭 SSE 连接
                        sink.complete();
                    }

                    // LLM 调用出错，被调一次
                    @Override
                    public void onError(Throwable error) {
                        // 把错误传给 Flux → 浏览器收到 error 事件
                        sink.error(error);
                    }
                })
        );
    }
}
