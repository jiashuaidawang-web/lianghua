package com.quant.agent.interfaces.http;

import com.quant.agent.application.llm.StructuredAnalysisService;
import com.quant.agent.domain.output.StockAnalysis;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// ============================================================================================
// 【Day 2 · 阅读入口】StructuredAnalysisController —— Day 2 的 HTTP 入口。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 2 调用链的"最上游"。
//     浏览器 → StructuredAnalysisController → StructuredAnalysisService → AiService 代理 → LLM
//   建议阅读时机：Day 2 最后读它（它只是水龙头）。
//   学完能回答：为什么返回类型写 StockAnalysis，浏览器拿到的就是 JSON？
//
//   💡 返回强类型对象怎么变成 JSON？
//     Spring WebFlux 看到返回类型是 StockAnalysis（不是 Flux/Mono），
//     就用 HTTP 消息转换器（HttpMessageWriter）把它序列化成 JSON 响应体。
//     响应头 Content-Type: application/json。
//     浏览器拿到：{"action":"BUY","symbol":"600519","score":8.5,"reason":"业绩稳健"}
//
//   ⚠ 和 Day 1 的 LlmStreamController 对比：
//     - Day 1 返回 Flux<String> + produces=TEXT_EVENT_STREAM → 流式 SSE
//     - Day 2 返回 StockAnalysis（普通对象） → 一次性 JSON
//     两者都是 WebFlux，但"返回类型 + produces"决定了输出形态。
//
//   ⬇ Day 2 到这里结束。下一步进入 Day 3：看 StockAnalysisWithToolAiService（工具调用版）。
// ============================================================================================

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
    // 返回 StockAnalysis → WebFlux 自动序列化成 JSON
    public StockAnalysis analyze(@RequestParam String symbol) {
        return analysisService.analyze(symbol);
    }
}
