# Day 6 Tasks

- [x] Confirm current API from official docs/Javadoc.
- [x] Implement domain contract.
- [x] Implement infrastructure adapter/node/tool.
- [x] Wire the application path.
- [x] Add normal-path JUnit 5 test.
- [x] Add error/boundary JUnit 5 test.
- [x] Run `mvn test`.
- [x] Update README/checklist with observed results.

## 实现说明

| Task | 实际落点 |
|------|---------|
| Confirm API | LangGraph4j 1.8.26 `addConditionalEdges` + `AsyncEdgeAction` + `StateGraph.END` |
| domain contract | 终止策略从 reviewNode 内常量 → 图拓扑层常量 `MAX_PLAN_ATTEMPT`（`QuantAgentStateGraph`） |
| adapter/node/tool | `ReviewNode`（删强制 pass）+ `QuantAgentStateGraph.compileDay5`（条件边加终止判断 + 路由表加 END） |
| application path | 无新增路径，复用 `GraphRunner.runDay5()` |
| normal-path test | `ReviewNodeTest.shouldFailHonestlyEvenWhenMaxAttemptsReached` |
| error/boundary test | `QuantAgentStateGraphTest.shouldTerminateDirectlyToEndWhenReplanExhausted`（3 次 fail 后直接 END，renderNode never invoked） |
| mvn test | 38 tests, BUILD SUCCESS |
| docs/checklist | checklist.md + tasks.md 更新 |

## 核心改动（3 处）

1. **`ReviewNode.java`**：删 `MAX_PLAN_ATTEMPT` 常量 + 强制 pass 分支；reviewNode 只管诚实审查（hasFailure → "fail"，否则 "pass"）
2. **`QuantAgentStateGraph.compileDay5`**：条件边 lambda 加终止判断 `if (planAttempt >= MAX) return END`；路由表加 `END→END`
3. **`QuantAgentStateGraph`**：新增 `MAX_PLAN_ATTEMPT` 常量（从 reviewNode 移过来）

## Vertical Evolution Contract

### Capability
Re-planning Capability

### Before
Day 5 Planner 一次性生成计划后执行。

### After
Agent 能发现失败/缺口，有限次数回到 Planner 重规划。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 5 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
