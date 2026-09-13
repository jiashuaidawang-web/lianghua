package com.quant.agent.infrastructure.llm;

import com.quant.agent.application.llm.StockAnalysisAiService;
import com.quant.agent.application.llm.StockAnalysisWithToolAiService;
import com.quant.agent.application.planner.PlannerAiService;
import com.quant.agent.application.planner.PlannerService;
import com.quant.agent.application.render.RenderAiService;
import com.quant.agent.application.tool.StockTools;
import com.quant.agent.graph.handlers.TaskHandler;
import com.quant.agent.graph.nodes.AnalysisNode;
import com.quant.agent.graph.nodes.ExecutorNode;
import com.quant.agent.graph.nodes.OutputNode;
import com.quant.agent.graph.nodes.PlannerNode;
import com.quant.agent.graph.nodes.RenderNode;
import com.quant.agent.graph.nodes.ReviewNode;
import com.quant.agent.graph.nodes.ToolNode;
import com.quant.agent.graph.runtime.GraphRunner;
import com.quant.agent.graph.topology.QuantAgentStateGraph;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// ============================================================================================
// 【Day 1~4 · 阅读入口】AiServicesConfiguration —— Spring 的"装配车间"，把所有零件装成 Bean。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 4 的"终点"（装配），也是 Day 1~4 所有 Bean 的集中地。
//   建议阅读时机：Day 4 最后读它（先看懂每个零件是怎么造的，再看它们怎么被组装）。
//   学完能回答：
//     1. AiServices.create / AiServices.builder 到底生成了什么？
//     2. Day 4 新增的 5 个 Graph Bean 是怎么被装配的？依赖关系是什么？
//     3. 为什么 Bean 方法要这样排序（先底层后顶层）？
//
//   💡 这个文件分两部分：
//     - Day 1~3 部分：创建 AiServices 代理（把接口变成 LLM 调用）
//     - Day 4 部分：创建图的所有组件（Node → Graph → Runner）
//
//   💡 Bean 依赖关系图（Day 4 新增部分，从底到顶）：
//
//     chatLanguageModel (LlmConfiguration 提供)
//         │
//         ▼
//     stockAnalysisAiService ──────→ analysisNode ─┐
//         │                                        │
//     stockTools ──────────────────→ toolNode ─────┼──→ quantAgentStateGraph → graphRunner
//                                                  │
//     outputNode (无依赖) ──────────────────────────┘
//
//     注意：outputNode 在最底层（无依赖），graphRunner 在最顶层（依赖所有）。
//     Spring 按依赖顺序自动注入，你不需要关心创建顺序。
//
//   💡 为什么 Bean 方法按"从底到顶"排序？
//     不是技术需要（Spring 不要求顺序），而是"阅读友好"：
//       先读底层依赖（AiService / StockTools）
//       再读中层组件（三个 Node）
//       最后读顶层装配（Graph → Runner）
//     这样从上到下读文件，就是"从零件到整机"的顺序。
//
//   ⬇ 到这里，Day 0~4 全部代码都加了注释。下一步：看测试文件验证理解，或进入 Day 5。
// ============================================================================================

@Configuration
public class AiServicesConfiguration {

    // ========================================================================
    // Day 2：创建 AiServices 代理（把接口变成 LLM 调用）
    // ========================================================================
    // AiServices.create(...) 在运行时动态生成 StockAnalysisAiService 的实现类。
    // 等价于：JDK 动态代理 / ByteBuddy 生成字节码。
    // 调用 stockAnalysisAiService.analyze("茅台") → 代理拦截 → 调 LLM → 反序列化 → 返回 StockAnalysis。
    @Bean
    public StockAnalysisAiService stockAnalysisAiService(ChatModel chatLanguageModel) {
        return AiServices.create(StockAnalysisAiService.class, chatLanguageModel);
    }


    // ========================================================================
    // Day 3：支持工具调用的 AiService 代理
    // ========================================================================
    // 和上面唯一的区别：通过 .tools(stockTools) 注入 @Tool 方法。
    // 注册后，LLM 就知道"我可以调 getStockPrice / getFundamental 获取数据"，
    // 并在需要时自动发起 tool_use → 框架调 Java → 结果塞回对话 → LLM 继续。
    @Bean
    public StockAnalysisWithToolAiService stockAnalysisWithToolAiService(
            ChatModel chatLanguageModel,
            StockTools stockTools) {
        return AiServices.builder(StockAnalysisWithToolAiService.class)
                .chatModel(chatLanguageModel)
                .tools(stockTools)  // ← 关键：把 @Tool 方法注册给代理
                .build();
    }

    // ========================================================================
    // Day 4：Graph Runtime Beans（从底到顶装配）
    // ========================================================================

    // ---- 第 1 层：三个 Node（工人）-------------------------------------------
    // 每个 Node 注入它需要的服务（Day 2 的 / Day 3 的），形成"节点 = 能力封装"。

    @Bean
    public AnalysisNode analysisNode(StockAnalysisAiService stockAnalysisAiService) {
        // AnalysisNode 内部用 StructuredAnalysisService（带校验+重试）包了一层 AiService
        return new AnalysisNode(
                new com.quant.agent.application.llm.StructuredAnalysisService(stockAnalysisAiService));
    }

    @Bean
    public ToolNode toolNode(StockTools stockTools) {
        return new ToolNode(stockTools);
    }

    @Bean
    public OutputNode outputNode() {
        // OutputNode 无依赖（纯确定性逻辑），直接 new
        return new OutputNode();
    }

    // ---- 第 2 层：图（把节点连起来）-----------------------------------------
    // QuantAgentStateGraph 注入三个 Node，compile() 时把它们连成图拓扑。

    @Bean
    public QuantAgentStateGraph quantAgentStateGraph(AnalysisNode analysisNode,
                                                     ToolNode toolNode,
                                                     OutputNode outputNode) {
        return new QuantAgentStateGraph(analysisNode, toolNode, outputNode);
    }

    // ---- 第 3 层：运行入口（一键开工）---------------------------------------
    // GraphRunner 注入 QuantAgentStateGraph，封装 compile + invoke。

    @Bean
    public GraphRunner graphRunner(QuantAgentStateGraph quantAgentStateGraph) {
        return new GraphRunner(quantAgentStateGraph);
    }

    // ========================================================================
    // Day 5：Planner Runtime Beans（从底到顶装配）
    // ========================================================================

    // ---- Planner 代理：调 LLM 生成 Task 列表 ---------------------------
    // ⚠ 注入的是 plainChatLanguageModel（无 Schema 约束），不是 chatLanguageModel。
    //    chatLanguageModel 硬编码了 StockAnalysis 的 responseFormat，
    //    复用会导致 planner 返回的 List<Task> 被强制扭曲成 StockAnalysis 对象 → 反序列化失败。
    @Bean
    public PlannerAiService plannerAiService(ChatModel plainChatLanguageModel) {
        return AiServices.create(PlannerAiService.class, plainChatLanguageModel);
    }

    // ---- Planner 应用服务：调代理 + 校验 + 重试 ------------------------
    @Bean
    public PlannerService plannerService(PlannerAiService plannerAiService) {
        return new PlannerService(plannerAiService);
    }

    // ---- 第 1 层：三个新 Node（工人）----------------------------------
    @Bean
    public PlannerNode plannerNode(PlannerService plannerService) {
        return new PlannerNode(plannerService);
    }

    @Bean
    public ExecutorNode executorNode(List<TaskHandler> handlers) {
        // Spring 自动注入所有 TaskHandler 实现类
        return new ExecutorNode(handlers);
    }

    @Bean
    public ReviewNode reviewNode() {
        return new ReviewNode();
    }

    // ---- 渲染代理 + 节点：LLM 润色结构化结果为人话 ----------------
    @Bean
    public RenderAiService renderAiService(ChatModel plainChatLanguageModel) {
        // 用 plainChatLanguageModel（无 Schema 约束），渲染结果不需要强制 JSON
        return AiServices.create(RenderAiService.class, plainChatLanguageModel);
    }

    @Bean
    public RenderNode renderNode(RenderAiService renderAiService) {
        return new RenderNode(renderAiService);
    }

    // ---- 第 2 层：图（把节点连起来）---------------------------------
    // Day 5 重构图拓扑：planner → executor → review →(pass→render→END / fail→planner)

    @Bean
    public QuantAgentStateGraph quantAgentStateGraphDay5(PlannerNode plannerNode,
                                                         ExecutorNode executorNode,
                                                         ReviewNode reviewNode,
                                                         RenderNode renderNode) {
        return new QuantAgentStateGraph(plannerNode, executorNode, reviewNode, renderNode);
    }

    // ---- 第 3 层：运行入口（一键开工）-------------------------------

    @Bean
    public GraphRunner graphRunnerDay5(QuantAgentStateGraph quantAgentStateGraphDay5) {
        return new GraphRunner(quantAgentStateGraphDay5);
    }
}
