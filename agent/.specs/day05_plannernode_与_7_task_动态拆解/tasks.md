# Day 5 Tasks

- [ ] Confirm current API from official docs/Javadoc.
- [ ] Implement domain contract.
- [ ] Implement infrastructure adapter/node/tool.
- [ ] Wire the application path.
- [ ] Add normal-path JUnit 5 test.
- [ ] Add error/boundary JUnit 5 test.
- [ ] Run `mvn test`.
- [ ] Update README/checklist with observed results.

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
