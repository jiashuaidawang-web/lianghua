package com.quant.agent.infrastructure.llm;

import com.quant.agent.application.llm.StockAnalysisAiService;
import com.quant.agent.application.llm.StockAnalysisWithToolAiService;
import com.quant.agent.application.tool.StockTools;
import com.quant.agent.graph.nodes.AnalysisNode;
import com.quant.agent.graph.nodes.OutputNode;
import com.quant.agent.graph.nodes.ToolNode;
import com.quant.agent.graph.runtime.GraphRunner;
import com.quant.agent.graph.topology.QuantAgentStateGraph;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiServicesConfiguration {

    /**
     * 创建 AiServices 代理：将 StockAnalysisAiService 接口变成 LLM 调用。
     *
     * <p>类比：Feign 的 @EnableFeignClients —— 接口自动变成远程调用代理。
     */
    @Bean
    public StockAnalysisAiService stockAnalysisAiService(ChatModel chatLanguageModel) {
        return AiServices.create(StockAnalysisAiService.class, chatLanguageModel);
    }

    /**
     * 支持工具调用的 AiService 代理。
     *
     * <p>关键区别：通过 .tools(stockTools) 注入 @Tool 方法，
     * LLM 可以自主决定调用哪些工具获取数据。
     */
    @Bean
    public StockAnalysisWithToolAiService stockAnalysisWithToolAiService(
            ChatModel chatLanguageModel,
            StockTools stockTools) {
        return AiServices.builder(StockAnalysisWithToolAiService.class)
                .chatModel(chatLanguageModel)
                .tools(stockTools)
                .build();
    }

    // ---- Day 04: Graph Runtime Beans ----

    @Bean
    public AnalysisNode analysisNode(StockAnalysisAiService stockAnalysisAiService) {
        return new AnalysisNode(
                new com.quant.agent.application.llm.StructuredAnalysisService(stockAnalysisAiService));
    }

    @Bean
    public ToolNode toolNode(StockTools stockTools) {
        return new ToolNode(stockTools);
    }

    @Bean
    public OutputNode outputNode() {
        return new OutputNode();
    }

    @Bean
    public QuantAgentStateGraph quantAgentStateGraph(AnalysisNode analysisNode,
                                                     ToolNode toolNode,
                                                     OutputNode outputNode) {
        return new QuantAgentStateGraph(analysisNode, toolNode, outputNode);
    }

    @Bean
    public GraphRunner graphRunner(QuantAgentStateGraph quantAgentStateGraph) {
        return new GraphRunner(quantAgentStateGraph);
    }
}
