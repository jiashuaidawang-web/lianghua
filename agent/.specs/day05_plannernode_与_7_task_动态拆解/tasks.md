# Day 5 Tasks

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
| Confirm API | LangGraph4j 1.8.26 StateGraph / AsyncNodeAction / AsyncEdgeAction |
| domain contract | `TaskType`(7 枚举) + `Task`(record, Serializable) |
| adapter/node/tool | `PlannerAiService`/`PlannerService` + `PlannerNode`/`ExecutorNode`/`ReviewNode` + `TaskHandler` ×3 |
| application path | `AiServicesConfiguration` Day5 beans + `GraphRunner.runDay5()` + `PlannerController` |
| normal-path test | `PlannerServiceTest`/`PlannerNodeTest`/`ExecutorNodeTest`/`ReviewNodeTest` + `QuantAgentStateGraphTest` Day5 |
| error/boundary test | 重试耗尽、target 为空、未知 TaskType、重规划上限、review fail→pass 循环 |
| mvn test | 34 tests, BUILD SUCCESS |
| docs/checklist | checklist.md + tasks.md 更新 |

## Vertical Evolution Contract

### Capability
Planner Capability

### Before
Day 4 已有图和 State，但任务流是固定的。

### After
Agent 可以动态规划 7 类标准原子任务，并将计划纳入后续执行。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 4 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
