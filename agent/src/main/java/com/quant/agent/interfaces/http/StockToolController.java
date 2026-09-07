package com.quant.agent.interfaces.http;

import com.quant.agent.application.llm.StockAnalysisWithToolAiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工具调用端点。
 *
 * <p>LLM 可以自主调用 @Tool 工具获取数据，再生成分析。
 */
@RestController
@RequestMapping("/api/v1/tool")
public class StockToolController {

    private final StockAnalysisWithToolAiService toolAiService;

    public StockToolController(StockAnalysisWithToolAiService toolAiService) {
        this.toolAiService = toolAiService;
    }

    /**
     * 分析股票（LLM 可调用工具获取数据）
     */
    @GetMapping("/analyze/{symbol}")
    public String analyze(@PathVariable String symbol) {
        return toolAiService.analyzeWithTools(symbol);
    }
}
