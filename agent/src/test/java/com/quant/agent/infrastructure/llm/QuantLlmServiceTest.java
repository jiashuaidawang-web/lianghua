package com.quant.agent.infrastructure.llm;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class QuantLlmServiceTest {

    @Test
    void shouldStreamPartialResponses() {
        StreamingChatModel model = mock(StreamingChatModel.class);

        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onPartialResponse("你");
            handler.onPartialResponse("好");
            handler.onCompleteResponse((ChatResponse) null);
            return null;
        }).when(model).chat(eq("hello"), any(StreamingChatResponseHandler.class));

        QuantLlmService service = new QuantLlmService(model);

        StepVerifier.create(service.streamChat("hello"))
                .expectNext("你", "好")
                .verifyComplete();
    }

    @Test
    void shouldPropagateErrors() {
        StreamingChatModel model = mock(StreamingChatModel.class);
        RuntimeException expected = new RuntimeException("LLM error");

        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onError(expected);
            return null;
        }).when(model).chat(eq("hello"), any(StreamingChatResponseHandler.class));

        QuantLlmService service = new QuantLlmService(model);

        StepVerifier.create(service.streamChat("hello"))
                .expectErrorMatches(error -> error == expected)
                .verify();
    }
}
