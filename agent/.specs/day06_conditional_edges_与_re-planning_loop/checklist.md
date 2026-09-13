# Day 6 Checklist

- [x] Function works on the happy path.
- [x] Error path is covered.
- [x] State/DTO/Tool result is validated.
- [x] No real credentials committed.
- [x] No beta/snapshot dependency added.
- [x] Unit tests pass.
- [x] Manual verification completed where applicable.

## Test Results（实测证据）

```
Tests run: 38, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

新增/修改测试（3 个）：
- `ReviewNodeTest.shouldFailHonestlyEvenWhenMaxAttemptsReached` —— Day 6 改动后 reviewNode 诚实审查，不再强制 pass
- `QuantAgentStateGraphTest.shouldTerminateDirectlyToEndWhenReplanExhausted` —— Day 6 核心：3 次 fail 后直接走 END，不经过 render，renderNode never invoked
- 删除了 2 个 debug 专用测试（`debugReplanExhaustionWithThreeFailures` / `debugReplanExhaustionWithRealPlannerNode`）和 1 个 Day5 旧测试（`shouldForcePassWhenMaxAttemptsReached`）

核心改动（3 处）：
- `ReviewNode.java` —— 删 `MAX_PLAN_ATTEMPT` 常量 + 强制 pass 分支；reviewNode 只诚实审查
- `QuantAgentStateGraph.java` —— 条件边 lambda 加终止判断（`planAttempt >= MAX → END`）；路由表加 `END→END`；`MAX_PLAN_ATTEMPT` 常量移至此处
- 职责分离：审查（reviewNode）与循环策略（条件边路由）解耦

## Vertical Evolution Contract

### Capability
Re-planning Capability

### Before
Day 5 Planner 一次性生成计划后执行；终止策略硬编码在 reviewNode 常量里，耗尽时伪造 pass。

### After
Agent 能发现失败/缺口，有限次数回到 Planner 重规划；终止策略显式化到条件边路由函数，耗尽直接走 END，reviewNode 诚实审查。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 5 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。

### Regression Results（回归证据）

| Day | 核心能力 | 测试类 | 结果 |
|-----|---------|--------|------|
| Day1 | LLM Streaming | `Day01QuantLlmServiceTest` (2 tests) | ✅ PASS |
| Day2 | Structured Output | `Day02StructuredAnalysisServiceTest` (4 tests) | ✅ PASS |
| Day3 | Tool Calling | `Day03StockToolsTest` (2 tests) | ✅ PASS |
| Day4 | Graph Runtime + 条件边 | `QuantAgentStateGraphTest` Day4 (3 tests) | ✅ PASS |
| Day4 | State 体系 | `QuantAgentStateTest` (4 tests) | ✅ PASS |
| Day5 | Planner + 重规划循环 | `QuantAgentStateGraphTest` Day5 (4 tests) + `PlannerServiceTest` (8 tests) | ✅ PASS |
| Day6 | 终止策略显式化 | `ReviewNodeTest` (3 tests) + `QuantAgentStateGraphTest` Day6 (1 new test) | ✅ PASS |

全部历史能力无退化。

## Day-specific Definition of Done（engineering prompt §9）

- [x] 今日 Capability 已插入现有 `/lianghua/agent`，不是独立 Demo
- [x] 旧能力保持可用（Day1~5 测试全绿）
- [x] Spec / Plan / Tasks / Checklist 已更新
- [x] 新增测试通过（38 tests, 0 failures）
- [x] 关键历史能力 Regression 通过（见上表）
- [x] Git diff 可解释（待 commit）

## Day 06 特殊验收

- [x] Re-plan 必须有显式 termination policy（最大次数/状态），不能形成无限 Loop。
  - 验证：`shouldTerminateDirectlyToEndWhenReplanExhausted` 测试证明 3 次后直接走 END。
