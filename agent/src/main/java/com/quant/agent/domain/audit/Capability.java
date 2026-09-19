package com.quant.agent.domain.audit;

import java.util.List;

// ============================================================================================
// 【Day 10 · 阅读入口】Capability —— 单个"能力"的描述，CapabilityInventory 的产出单元。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 10 能力清单的最小单元。一条记录 = "Agent 能做的一件事"。
//   学完能回答：
//     1. Capability 和 Task 是什么关系？
//     2. 为什么要有 source 字段？
//     3. 为什么 tags 用 List 不用枚举？
//
//   💡 Capability 和 Task 的关系：
//     Capability = "我能做什么"（能力清单，由系统扫描产生）
//     Task      = "我要做什么"（任务列表，由 Planner 规划产生）
//     Diff 引擎对比两者 → 找出"Task 有但 Capability 没有"的缺口。
//
//   💡 为什么有 source 字段？
//     区分能力来自哪：
//       LOCAL  = 本 JVM 的 @Tool 方法（如 StockTools.getStockPrice）
//       MCP    = 远端 MCP Server 暴露的工具（通过 McpClient.listTools 发现）
//     这决定了"缺口怎么补"——LOCAL 缺就加 @Tool，MCP 缺就连远端。
//
//   💡 为什么 tags 用 List<String> 不用枚举？
//     能力是动态发现的（扫描 + MCP 反射），枚举会限制扩展。
//     tags 让 Diff 引擎能做模糊匹配（如 Task 要"资金数据"，Capability 有 tag="market-data" → 算部分匹配）。
//
//   ⬇ 下一步：看 CapabilityInventory（怎么扫描出这些 Capability）。
// ============================================================================================

/**
 * 单个能力描述。
 *
 * <p>CapabilityInventory 扫描本地 @Tool + MCP 远端工具，产出 List&lt;Capability&gt;。
 *
 * @param name        能力名（与工具名一致，如 "getStockPrice"）
 * @param type        能力类型（DATA / ANALYSIS / EXECUTE / NOTIFY 等）
 * @param source      能力来源（LOCAL = 本 JVM  /  MCP = 远端 MCP Server）
 * @param description 工具描述（来自 @Tool 的 value 或 MCP ToolDefinition）
 * @param tags        能力标签（用于模糊匹配，如 "market-data", "price", "fundamental"）
 */
public record Capability(
        String name,
        CapabilityType type,
        CapabilitySource source,
        String description,
        List<String> tags) {

    /**
     * 能力类型：覆盖 TaskType 的主要类别。
     * <p>注意：CapabilityType 是 Capability 的"类别"，不是 TaskType 本身。
     * 一个 CapabilityType 可对应多个具体工具（如 DATA 对应 getStockPrice + getFundamental）。
     */
    public enum CapabilityType {
        ANALYSIS,   // 分析类（调 LLM 推理）
        DATA,       // 数据类（获取真实数据）
        EXECUTE,    // 执行类（有副作用的操作）
        NOTIFY,     // 通知类
        FILTER,     // 筛选类
        REPORT,     // 报告类
        MCP_REMOTE, // 远端 MCP 工具（类型未知时的兜底）
        SANDBOX,    // 沙盒执行类（Day 11：容器隔离执行 LLM 生成的代码）
        UNKNOWN     // 未知类型
    }

    /**
     * 能力来源。
     */
    public enum CapabilitySource {
        LOCAL,   // 本 JVM 的 @Tool 方法
        MCP      // 远端 MCP Server 暴露的工具
    }
}
