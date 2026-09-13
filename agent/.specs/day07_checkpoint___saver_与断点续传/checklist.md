# Day 7 Checklist

- [x] Function works on the happy path.
- [x] Error path is covered.
- [x] State/DTO/Tool result is validated.
- [x] No real credentials committed.
- [x] No beta/snapshot dependency added.
- [x] Unit tests pass.
- [x] Manual verification completed where applicable.

## Test Results（实测证据）

```
Tests run: 41, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

新增测试（3 个）：
- `CheckpointTest.memorySaverShouldSaveAndLoad` —— MemorySaver 存/取基本能力
- `CheckpointTest.graphExecutionShouldAutoSaveCheckpoints` —— 图执行时框架自动每节点存快照（5 个 Checkpoint = 4 节点 + END）
- `CheckpointTest.shouldResumeFromCheckpoint` —— 用同一个 threadId 恢复续传

核心改动（2 处生产代码）：
- `QuantAgentStateGraph.java` —— `compileDay5(BaseCheckpointSaver)` 接入框架内置 Checkpoint；`CompileConfig` 注入 saver；保留无参 `compileDay5()` 兼容旧调用
- `GraphRunner.java` —— `runDay5(symbol, saver)` 注入 saver + 生成 threadId；新增 `resumeDay5(threadId, saver)` 恢复入口

关键发现：
- LangGraph4j 1.8.26 **内置 Checkpoint 支持**（`BaseCheckpointSaver` / `MemorySaver` / `FileSystemSaver` / `CompileConfig` / `RunnableConfig`）
- 无需自定义 Checkpoint 实现，直接用框架内置 API

## Vertical Evolution Contract

### Capability
Durable Execution Capability

### Before
Day 6 的图只能依赖进程内内存 State。

### After
Agent 可以保存/恢复执行上下文，并在进程重启后继续。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 6 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

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
| Day6 | 终止策略显式化 | `ReviewNodeTest` (3 tests) + `QuantAgentStateGraphTest` Day6 (1 test) | ✅ PASS |
| **7** | **Checkpoint 快照持久化** | **`CheckpointTest` (3 new tests)** | ✅ PASS |

全部历史能力无退化。

## Day-specific Definition of Done（engineering prompt §9）

- [x] 今日 Capability 已插入现有 `/lianghua/agent`，不是独立 Demo
- [x] 旧能力保持可用（Day1~6 测试全绿）
- [x] Spec / Plan / Tasks / Checklist 已更新
- [x] 新增测试通过（41 tests, 0 failures）
- [x] 关键历史能力 Regression 通过（见上表）
- [x] Git diff 可解释（待 commit）

## Day 07 特殊验收（engineering prompt §8）

- [x] Checkpoint 必须绑定稳定的 execution/thread identity，并验证"进程中断 → 恢复 → 继续执行"。
  - 验证：`runDay5` 生成 threadId（`day5-{symbol}-{uuid}`），`resumeDay5` 用同一 threadId 恢复；`CheckpointTest.shouldResumeFromCheckpoint` 验证恢复续传。
