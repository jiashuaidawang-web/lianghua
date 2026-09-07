package com.quant.agent.application.llm;

import com.quant.agent.domain.output.StockAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 结构化分析应用服务。
 *
 * <p>职责：通过 AiServices 代理调用 LLM → 拿到 DTO → 校验 → 合法返回 / 非法重试。
 *
 * <p>LLM 输出视为不可信输入：反序列化后必须校验，非法时进入重试或明确失败路径。
 */
@Service
public class StructuredAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(StructuredAnalysisService.class);

    /** 最大重试次数：LLM 可能不遵守 Schema，允许有限重试 */
    private static final int MAX_RETRY = 2;

    private final StockAnalysisAiService analysisAiService;

    public StructuredAnalysisService(StockAnalysisAiService analysisAiService) {
        this.analysisAiService = analysisAiService;
    }

    /**
     * 分析股票，返回结构化结果。
     *
     * <p>失败恢复策略：最多重试 MAX_RETRY 次，仍失败则抛明确异常。
     *
     * @param symbol 股票代码或名称
     * @return 合法的 StockAnalysis
     * @throws IllegalStateException 重试耗尽仍无法获得合法结果
     */
    public StockAnalysis analyze(String symbol) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                log.info("结构化分析请求: symbol={}, attempt={}/{}", symbol, attempt, MAX_RETRY);

                // 调用 AiServices 代理（底层调 LLM，同步阻塞拿到完整结果）
                StockAnalysis result = analysisAiService.analyze(symbol);

                // 校验：LLM 输出视为不可信输入
                if (result != null && result.isValid()) {
                    log.info("结构化分析成功: {}", result);
                    return result;
                }

                log.warn("LLM 输出非法，准备重试: result={}, attempt={}/{}", result, attempt, MAX_RETRY);

            } catch (Exception e) {
                // 反序列化失败（LLM 返回纯文本 / 非法 JSON）会抛异常
                log.warn("结构化分析异常，准备重试: error={}, attempt={}/{}", e.getMessage(), attempt, MAX_RETRY);
            }
        }

        throw new IllegalStateException(
                "结构化分析失败：重试 " + MAX_RETRY + " 次后仍无法获得合法结果, symbol=" + symbol);
    }
}
