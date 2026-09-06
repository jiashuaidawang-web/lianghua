package com.quant.agent.infrastructure.llm;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
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
                sink.complete();
            }

            @Override
            public void onError(Throwable error) {
                sink.error(error);
            }
        }));
    }
}
