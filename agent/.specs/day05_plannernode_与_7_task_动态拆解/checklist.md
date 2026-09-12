# Day 5 Checklist

- [x] Function works on the happy path.
- [x] Error path is covered.
- [x] State/DTO/Tool result is validated.
- [x] No real credentials committed.
- [x] No beta/snapshot dependency added.
- [x] Unit tests pass.
- [x] Manual verification completed where applicable.

## Test Results（实测证据）

```
Tests run: 34, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

新增 Day5 测试（4 个）：
- `shouldCompileDay5Successfully` —— Day5 图编译成功
- `shouldPassThroughDay5FlowWhenReviewPasses` —— review 一次 pass，无循环
- `shouldReplanOnceWhenReviewFailsThenPasses` —— fail→重规划→pass，循环正确结束
- `shouldReplanLoopInvokeNodesCorrectTimes` —— 精确验证循环时 planner/executor/review 各被调 2 次

集成测试发现的 bug 已修复：
- `Task` record 未实现 `Serializable` → LangGraph4j 序列化 State 抛 `NotSerializableException`。
  已让 `Task implements Serializable`（也为 Day7 Checkpoint 铺路）。

## Vertical Evolution Contract

### Capability
Planner Capability

### Before
Day 4 已有图和 State，但任务流是固定的。

### After
Agent 可以动态规划 7 类标准原子任务，并将计划纳入后续执行；支持重规划循环（review fail → planner）。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 4 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。

### Regression Results（回归证据）

| Day | 核心能力 | 测试类 | 结果 |
|-----|---------|--------|------|
| Day1 | LLM Streaming | `Day01QuantLlmServiceTest` (2 tests) | ✅ PASS |
| Day2 | Structured Output | `Day02StructuredAnalysisServiceTest` (4 tests) | ✅ PASS |
| Day3 | Tool Calling | `Day03StockToolsTest` (2 tests) | ✅ PASS |
| Day4 | Graph Runtime + 条件边 | `QuantAgentStateGraphTest` Day4 (3 tests) + `AnalysisNodeTest` (3 tests) | ✅ PASS |
| Day4 | State 体系 | `QuantAgentStateTest` (4 tests) | ✅ PASS |

全部历史能力无退化。

## Day-specific Definition of Done（engineering prompt §9）

- [x] 今日 Capability 已插入现有 `/lianghua/agent`，不是独立 Demo
- [x] 旧能力保持可用（Day1~4 测试全绿）
- [x] Spec / Plan / Tasks / Checklist 已更新
- [x] 新增测试通过（34 tests, 0 failures）
- [x] 关键历史能力 Regression 通过（见上表）
- [x] Git diff 可解释（待 commit）
