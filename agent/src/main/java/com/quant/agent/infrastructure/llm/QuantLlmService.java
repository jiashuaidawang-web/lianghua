package com.quant.agent.infrastructure.llm;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class QuantLlmService {

    private final StreamingChatModel model;

    public QuantLlmService(StreamingChatModel model) {
        this.model = model;
    }

    public Flux<String> streamChat(String userPrompt) {
        return Flux.create(sink -> model.chat(userPrompt, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                sink.next(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                // 完整文本
                String text = completeResponse.aiMessage().text();
                System.out.println("完整的文本:"+text);
                // token 用量
                TokenUsage tokenUsage = completeResponse.tokenUsage();
                System.out.println("token 用量:"+tokenUsage);
                // 为什么停
                FinishReason finishReason = completeResponse.finishReason();
                System.out.println("为什么停:"+finishReason);
                sink.complete();
            }

            @Override
            public void onError(Throwable error) {
                sink.error(error);
            }
        }));
    }
}
