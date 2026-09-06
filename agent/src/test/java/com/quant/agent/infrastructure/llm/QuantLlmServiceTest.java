package com.quant.agent.infrastructure.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuantLlmServiceTest {

    @Test
    void shouldStreamPartialResponses() {
        StreamingChatModel model = mock(StreamingChatModel.class);

        // 合法的 ChatResponse fixture：生产环境 onCompleteResponse 永远收到非 null 响应
        ChatResponse completeResponse = mock(ChatResponse.class);
        when(completeResponse.aiMessage()).thenReturn(AiMessage.from("你好"));
        when(completeResponse.tokenUsage()).thenReturn(new TokenUsage(10, 20));
        when(completeResponse.finishReason()).thenReturn(FinishReason.STOP);

        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onPartialResponse("你");
            handler.onPartialResponse("好");
            handler.onCompleteResponse(completeResponse);
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
