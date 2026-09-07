package com.quant.agent.interfaces.http;

import com.quant.agent.application.llm.StructuredAnalysisService;
import com.quant.agent.domain.output.StockAnalysis;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 结构化分析端点。
 *
 * <p>GET /api/v1/analysis/structured?symbol=贵州茅台
 * 返回强类型 JSON（StockAnalysis），而非纯文本流。
 */
@RestController
public class StructuredAnalysisController {

    private final StructuredAnalysisService analysisService;

    public StructuredAnalysisController(StructuredAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/api/v1/analysis/structured")
    public StockAnalysis analyze(@RequestParam String symbol) {
        return analysisService.analyze(symbol);
    }
}
