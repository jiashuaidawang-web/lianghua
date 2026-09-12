package com.quant.agent.graph.handlers;

import com.quant.agent.application.tool.StockTools;
import com.quant.agent.domain.task.Task;
import com.quant.agent.domain.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

// ============================================================================================
// 【Day 5 · 阅读入口】DataFetchTaskHandler —— 处理 DATA_FETCH 类型任务的策略实现。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：TaskHandler 接口的一个实现，负责执行 DATA_FETCH 类型的 Task。
//   建议阅读时机：读完 AnalysisTaskHandler 后读它。
//   学完能回答：
//     1. 这个 Handler 怎么根据 params 决定查什么数据？
//     2. 复用了 Day 3 的什么能力？
//
//   💡 怎么根据 params 决定查什么数据？
//     Task 的 params 是 Map<String, Object>，可以灵活传参。
//
//     示例：
//       { type: DATA_FETCH, target: "600519", params: { fields: ["price"] } }
//         → 只查价格
//       { type: DATA_FETCH, target: "600519", params: { fields: ["pe", "pb"] } }
//         → 查基本面
//
//     如果 params 里没传 fields → 默认查全部（价格 + 基本面）。
//
//   💡 复用了 Day 3 的什么？
//     这个 Handler 内部调的是 StockTools（Day 3 的 @Tool 方法）。
//     这就是「纵向演进、复用历史能力」—— Day 5 没有再造数据获取能力，而是把 Day 3 的
//     StockTools 塞进了一个 Handler 里。
//
//   ⬇ 下一步：看 ReportTaskHandler（处理 REPORT 类型的 Handler）。
// ============================================================================================

/**
 * DATA_FETCH 类型任务的 Handler。
 *
 * <p>复用 Day 3 的 StockTools（@Tool 方法获取真实数据）。
 */
@Component
public class DataFetchTaskHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(DataFetchTaskHandler.class);

    // 注入 Day 3 的工具集合
    private final StockTools stockTools;

    public DataFetchTaskHandler(StockTools stockTools) {
        this.stockTools = stockTools;
    }

    @Override
    public TaskType type() {
        return TaskType.DATA_FETCH;  // 我处理 DATA_FETCH 类型
    }

    @Override
    public String handle(Task task) {
        String target = task.target();
        log.info("DataFetchTaskHandler 执行: target={}", target);

        try {
            // -----------------------------------------------------------------
            // 根据 params 决定查什么数据
            // -----------------------------------------------------------------
            // 从 params 取 "fields" 参数，决定查哪些字段
            List<String> fields = task.getParam("fields", List.class);

            StringBuilder result = new StringBuilder();

            // 如果没有指定 fields，或者 fields 包含 "price" → 查价格
            if (fields == null || fields.contains("price")) {
                String price = stockTools.getStockPrice(target);
                result.append("价格=").append(price);
            }

            // 如果没有指定 fields，或者 fields 包含基本面相关 → 查基本面
            if (fields == null || fields.contains("pe") || fields.contains("pb") || fields.contains("fundamental")) {
                if (!result.isEmpty()) result.append("; ");
                String fundamental = stockTools.getFundamental(target);
                result.append("基本面=").append(fundamental);
            }

            return result.toString();

        } catch (Exception e) {
            log.warn("DataFetchTaskHandler 失败: target={}, error={}", target, e.getMessage());
            return "数据获取失败: " + e.getMessage();
        }
    }
}
