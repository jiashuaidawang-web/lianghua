package com.quant.agent.interfaces.http;

import com.quant.agent.application.llm.StockAnalysisWithToolAiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// ============================================================================================
// 【Day 3 · 阅读入口】StockToolController —— Day 3 的 HTTP 入口。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 3 调用链的"最上游"。
//     浏览器 → StockToolController → StockAnalysisWithToolAiService → LLM（可调 @Tool）
//   建议阅读时机：Day 3 最后读它。
//   学完能回答：Day 3 的完整工具调用循环在 HTTP 请求里是怎么体现的？
//
//   💡 Day 3 的完整调用链（一次 HTTP 请求 = 内部多轮 LLM 对话）：
//
//     浏览器 GET /api/v1/tool/analyze/600519
//       → StockToolController.analyze("600519")
//         → toolAiService.analyzeWithTools("600519")
//           ═══════════════════════════════════════════════════════
//           以下是 LangChain4j AiServices 代理内部自动做的（你看不见但要知道）：
//           ═══════════════════════════════════════════════════════
//           ① 组装消息：[SystemMessage(角色+工具提示), UserMessage("请分析股票：600519")]
//           ② 附带工具说明书：[getStockPrice 说明书, getFundamental 说明书]
//           ③ 发给 LLM
//           ④ LLM 返回 tool_use(getStockPrice, symbol="600519")  ← 不是文本！
//           ⑤ 代理拦截 tool_use → 调 Java 方法 stockTools.getStockPrice("600519")
//           ⑥ 拿到返回值 "{\"symbol\":\"600519\",\"price\":1500.0,...}"
//           ⑦ 把返回值作为 tool_result 塞回对话
//           ⑧ 再次发给 LLM
//           ⑨ LLM 可能还想查基本面 → 返回 tool_use(getFundamental, ...) → 重复⑤~⑧
//           ⑩ LLM 终于有数据了 → 返回纯文本分析（不再调工具）
//           ═══════════════════════════════════════════════════════
//         ← 拿到最终文本
//       ← 返回给浏览器
//
//   💡 为什么用 @PathVariable 不用 @RequestParam？
//     纯风格选择：/api/v1/tool/analyze/600519 比 ?symbol=600519 更像 RESTful 资源路径。
//     功能上没有区别。
//
//   ⚠ Day 3 的"代价"：
//     一次 HTTP 请求背后可能调了 N 次 LLM（1 次用户问题 + N 次工具调用），
//     延迟比 Day 2 高，token 消耗也多。
//     但换来的是"LLM 能用真实数据"，而不是凭训练记忆瞎编。
//
//   ⬇ Day 3 到这里结束。下一步进入 Day 4：看 StateKeys / QuantAgentState（状态基石）。
// ============================================================================================

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
     *
     * GET /api/v1/tool/analyze/600519
     * → 返回 LLM 基于工具数据的分析文本
     */
    @GetMapping("/analyze/{symbol}")
    public String analyze(@PathVariable String symbol) {
        return toolAiService.analyzeWithTools(symbol);
    }
}
