package com.quant.agent.interfaces.http;

import com.quant.agent.infrastructure.llm.QuantLlmService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class LlmStreamController {

    private final QuantLlmService service;

    public LlmStreamController(QuantLlmService service) {
        this.service = service;
    }

    @GetMapping(value = "/api/v1/llm/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam String prompt) {
        return service.streamChat(prompt);
    }
}
