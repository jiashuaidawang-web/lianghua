# Checklist — Day 04

## Definition of Done

- [x] **D1**: 今日 Capability 已插入现有 `/lianghua/agent`，不是独立 Demo
- [x] **D2**: `QuantAgentState` 继承 AgentState（Map 底层）+ 类型化 accessor
- [x] **D3**: StateGraph 含 3 节点、1 条件边，能 compile 成功
- [x] **D4**: Node 复用 Day 1~3 的 Application Service，不直接调 SDK
- [x] **D5**: 旧能力保持可用（Day 1 流式、Day 3 工具调用）
- [x] **D6**: Spec / Plan / Tasks / Checklist 已更新
- [x] **D7**: 新增测试通过（10 个新测试）
- [x] **D8**: 关键历史能力 Regression 通过（Day 1/2/3 共 8 个测试）
- [x] **D9**: Git diff 可解释

## 验收证据

- [x] `QuantAgentStateGraphTest` 覆盖条件边两个分支（needsTool true/false）
- [x] `AnalysisNodeTest` mock StructuredAnalysisService，验证 State 写入 + 失败路径
- [x] `GraphController` `GET /api/v1/graph/analyze?symbol=...` 可调通
- [x] `mvn test` 全绿（18 tests, 0 failures）

## 设计修正记录

- **Spec 假设 vs 实际 API 冲突**：原设计 `QuantAgentState` 包装 Map，实际 LangGraph4j 1.8.26 要求 `extends AgentState`。已修正为继承 + 类型化 accessor。
- **包装方法名**：`AsyncNodeAction.node()` → 实际为 `node_async()`；条件边用 `AsyncEdgeAction.edge_async()`。
- **invoke 入参**：传 `Map<String,Object>` 而非 State 对象，框架用 `AgentStateFactory` 构造。

## 未解决风险

- [ ] Map 存储的类型安全靠 accessor + 代码审查，无编译期保证
- [ ] 条件边 key 拼写错误只能靠测试捕获
- [ ] Day 4 的 `AnalysisNode` 目前 mock 测试，真实 LLM 集成待后续 Day 验证
