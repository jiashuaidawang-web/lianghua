package com.quant.agent.application.llm;

import com.quant.agent.domain.output.StockAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// ============================================================================================
// 【Day 2 · 阅读入口】StructuredAnalysisService —— "校验 + 重试"的防御层。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 2 的"应用服务"，夹在 AiService 代理和调用方之间。
//     调用方 → StructuredAnalysisService → StockAnalysisAiService（代理）→ LLM
//   建议阅读时机：读完 StockAnalysisAiService 接口后读它。
//   学完能回答：
//     1. 为什么不直接暴露 AiService 给上层，非要加这一层？
//     2. 重试策略是什么？什么情况重试？什么情况放弃？
//     3. 这体现了 constitution 里的哪条规则？
//
//   💡 为什么不直接暴露 AiService？
//     AiService 代理是"裸调用"：LLM 返回什么就返回什么，不做校验。
//     但 LLM 是概率性的，可能：
//       - 返回非法 JSON（反序列化抛异常）
//       - 返回合法 JSON 但字段非法（score=999、action="buy"小写）
//       - 返回 null
//     如果这些"脏数据"直接进下游，会污染状态、导致错误决策。
//     所以加一层 StructuredAnalysisService 做"校验 + 重试"的防御。
//
//   💡 重试策略详解：
//     MAX_RETRY = 2：最多尝试 2 次（不是重试 2 次，是总共最多 2 次）。
//     循环逻辑：
//       attempt=1：调 AiService → 成功且合法 → 返回（不调第 2 次）
//                 成功但非法 → 打 warn 日志 → 继续循环
//                 异常        → 打 warn 日志 → 继续循环
//       attempt=2：同上，但如果还是失败 → 循环结束 → 抛 IllegalStateException
//
//   💡 体现了 constitution 哪条规则？
//     第 2.4 条："LLM output is untrusted input: validate before state mutation or tool execution."
//     （LLM 输出是不可信输入：校验后才能改状态或执行工具）
//     这里在"进入下游之前"用 isValid() 校验，正是这条规则的落地。
//
//   💡 为什么抛 IllegalStateException 而不是返回 null？
//     返回 null 会让下游处处 if (x == null)，容易遗漏导致 NPE。
//     抛异常是"快速失败"（fail-fast）：调用方必须处理，不会默默吞掉错误。
//
//   ⬇ 下一步：
//     - 想看它被谁消费：Day 2 路线看 StructuredAnalysisController；Day 4 路线看 AnalysisNode。
//     - 想进入 Day 3：看 StockAnalysisWithToolAiService（支持工具调用的版本）。
// ============================================================================================

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

    // -------------------------------------------------------------------------
    // 最大尝试次数：LLM 可能不遵守 Schema，允许有限重试
    // -------------------------------------------------------------------------
    // 为什么是 2 而不是 0 或 10？
    //   - 0：不允许任何失败，太严格（LLM 偶尔抽风一次很正常）
    //   - 10：太多，延迟高且浪费 token，大概率是 prompt/Schema 本身有问题
    //   - 2：工程折中 —— 给一次"LLM 抽风"的机会，两次都失败说明真有问题
    private static final int MAX_RETRY = 2;

    // 注入的是 AiService 代理接口（不是实现类，实现类是 LangChain4j 运行时生成的）
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
     * @return 合法的 StockAnalysis（保证非 null 且 isValid() = true）
     * @throws IllegalStateException 重试耗尽仍无法获得合法结果
     */
    public StockAnalysis analyze(String symbol) {
        // 循环 MAX_RETRY 次，不是"重试 MAX_RETRY 次"（第一次不算重试）
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                log.info("结构化分析请求: symbol={}, attempt={}/{}", symbol, attempt, MAX_RETRY);

                // -----------------------------------------------------------------
                // 调用 AiServices 代理（底层调 LLM，同步阻塞拿到完整结果）
                // -----------------------------------------------------------------
                // 这一步是"阻塞"的：当前线程会等 LLM 把整个 JSON 生成完才继续。
                // 不能用流式（流式边生成边吐，拿不到完整 JSON 就没法反序列化）。
                StockAnalysis result = analysisAiService.analyze(symbol);

                // -----------------------------------------------------------------
                // 校验：LLM 输出视为不可信输入
                // -----------------------------------------------------------------
                // result != null：防止 LLM 返回空
                // result.isValid()：防止字段非法（score 越界、action 拼错等）
                if (result != null && result.isValid()) {
                    log.info("结构化分析成功: {}", result);
                    return result;  // 合法 → 立刻返回，不再重试
                }

                // 非法：打 warn 日志，继续循环（如果还有次数的话）
                log.warn("LLM 输出非法，准备重试: result={}, attempt={}/{}", result, attempt, MAX_RETRY);

            } catch (Exception e) {
                // -----------------------------------------------------------------
                // 异常：通常是反序列化失败（LLM 返回纯文本 / 非法 JSON）
                // -----------------------------------------------------------------
                // 也可能是网络超时、API 限流等。统一 catch Exception 是为了"任何错都重试"。
                log.warn("结构化分析异常，准备重试: error={}, attempt={}/{}", e.getMessage(), attempt, MAX_RETRY);
            }
        }

        // 循环结束还没 return → 说明 MAX_RETRY 次全失败了 → 快速失败
        throw new IllegalStateException(
                "结构化分析失败：重试 " + MAX_RETRY + " 次后仍无法获得合法结果, symbol=" + symbol);
    }
}
