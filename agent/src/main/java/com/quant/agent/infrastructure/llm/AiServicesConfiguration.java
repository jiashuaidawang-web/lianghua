package com.quant.agent.infrastructure.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.agent.application.audit.DiffAuditService;
import com.quant.agent.application.llm.StockAnalysisAiService;
import com.quant.agent.application.llm.StockAnalysisWithToolAiService;
import com.quant.agent.application.planner.PlannerAiService;
import com.quant.agent.application.planner.PlannerService;
import com.quant.agent.application.render.RenderAiService;
import com.quant.agent.application.tool.StockTools;
import com.quant.agent.graph.handlers.TaskHandler;
import com.quant.agent.infrastructure.audit.CapabilityInventory;
import com.quant.agent.infrastructure.mcp.McpServer;
import com.quant.agent.infrastructure.mcp.McpToolBridge;
import com.quant.agent.infrastructure.mcp.protocol.ImplementationInfo;
import com.quant.agent.infrastructure.tool.EastMoneyAdapter;
import com.quant.agent.infrastructure.tool.MarketDataCache;
import com.quant.agent.infrastructure.tool.MarketDataGateway;
import com.quant.agent.infrastructure.tool.RateLimiter;
import com.quant.agent.graph.nodes.AnalysisNode;
import com.quant.agent.graph.nodes.DiffAuditNode;
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
import org.springframework.context.ApplicationContext;
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
    // Day 8：Finance Data Tooling —— 限流 + 缓存 + Adapter + 降级兜底
    // ========================================================================
    // Day 8 新增的工程护栏链：
    //   StockTools → MarketDataGateway → [RateLimiter → Cache → EastMoneyAdapter]
    // 不新增第三方依赖，全部 JDK 原生实现。

    /**
     * Day 8：RateLimiter Bean —— 令牌桶限流（1 次/秒，桶容量 2）。
     * 为什么 1 次/秒？东财免费版限制 ~1s 1 次；桶容量 2 允许启动时小突发。
     */
    @Bean
    public RateLimiter marketDataRateLimiter() {
        return new RateLimiter(1.0, 2);
    }

    /**
     * Day 8：MarketDataCache Bean —— TTL 5 秒、最大 1000 条目、10 秒清理间隔。
     */
    @Bean
    public MarketDataCache marketDataCache() {
        return new MarketDataCache(5000L, 1000, 10_000L);
    }

    /**
     * Day 8：EastMoneyAdapter Bean —— 东财行情 API 适配器（启用降级兜底）。
     */
    @Bean
    public EastMoneyAdapter eastMoneyAdapter() {
        return new EastMoneyAdapter("https://push2.eastmoney.com", true);
    }

    /**
     * Day 8：MarketDataGateway Bean —— 编排限流→缓存→Adapter→降级。
     */
    @Bean
    public MarketDataGateway marketDataGateway(RateLimiter marketDataRateLimiter,
                                                MarketDataCache marketDataCache,
                                                EastMoneyAdapter eastMoneyAdapter) {
        return new MarketDataGateway(marketDataRateLimiter, marketDataCache, eastMoneyAdapter);
    }

    // ========================================================================
    // Day 3：支持工具调用的 AiService 代理
    // ========================================================================
    // Day 8 改动：stockTools 不再返回硬编码 Mock，改为通过 MarketDataGateway 获取受控数据。
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
    // Day 10 改动：review 之后插入 diffAudit 节点

    @Bean
    public QuantAgentStateGraph quantAgentStateGraphDay5(PlannerNode plannerNode,
                                                         ExecutorNode executorNode,
                                                         ReviewNode reviewNode,
                                                         RenderNode renderNode,
                                                         DiffAuditNode diffAuditNode) {
        return new QuantAgentStateGraph(plannerNode, executorNode, reviewNode, renderNode, diffAuditNode);
    }

    // ---- 第 3 层：Day 5 运行入口（PlannerController 依赖此 Bean，名为 graphRunnerDay5）----
    // 修复 Day 5 遗漏：之前只注册了 Day 4 的 graphRunner，PlannerController 注入 @Qualifier("graphRunnerDay5") 找不到 Bean。
    @Bean
    public GraphRunner graphRunnerDay5(QuantAgentStateGraph quantAgentStateGraphDay5) {
        return new GraphRunner(quantAgentStateGraphDay5);
    }

    // ========================================================================
    // Day 10：DSH Diff Audit —— 差分审计能力（能力缺口审计）
    // ========================================================================

    /**
     * Day 10：CapabilityInventory Bean —— 能力清单扫描器（扫描本地 @Tool + 远端 MCP）。
     */
    @Bean
    public CapabilityInventory capabilityInventory(ApplicationContext applicationContext) {
        return new CapabilityInventory(applicationContext);
    }

    /**
     * Day 10：DiffAuditService Bean —— 编排能力扫描 + Task 对比 + 产出 GapMatrix。
     */
    @Bean
    public DiffAuditService diffAuditService(CapabilityInventory capabilityInventory) {
        return new DiffAuditService(capabilityInventory);
    }

    /**
     * Day 10：DiffAuditNode Bean —— 差分审计节点（接入图的"审计闸门"）。
     */
    @Bean
    public DiffAuditNode diffAuditNode(DiffAuditService diffAuditService) {
        return new DiffAuditNode(diffAuditService);
    }

    /**
     * Day 9：ObjectMapper Bean —— MCP 协议序列化共用。
     *
     * <p>Spring Boot webflux 默认不暴露 ObjectMapper 为 Bean，McpToolBridge/McpServer/McpClient 需要注入。
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Day 9：MCP 工具桥接器 —— 反射 StockTools 的 @Tool 方法，生成 MCP 工具定义。
     */
    @Bean
    public McpToolBridge mcpToolBridge(StockTools stockTools, ObjectMapper objectMapper) {
        return new McpToolBridge(stockTools, objectMapper);
    }

    /**
     * Day 9：MCP Server 核心 —— 注册 StockTools 工具，处理 JSON-RPC 请求。
     */
    @Bean
    public McpServer mcpServer(McpToolBridge mcpToolBridge, ObjectMapper objectMapper) {
        McpServer server = new McpServer(objectMapper, new ImplementationInfo("lianghua-agent", "1.0.0"));
        // 把 bridge 扫描到的所有 @Tool 方法注册为 MCP 工具
        mcpToolBridge.buildToolBindings().forEach(binding ->
                server.registerTool(binding.definition(), binding.executor()));
        return server;
    }
}
