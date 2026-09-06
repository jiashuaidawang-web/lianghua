# Day 4 Tasks

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
Agent State & Graph Runtime

### Before
Day 1~3 是单调用/Tool 闭环。

### After
Quant Agent 拥有显式 State、Node、Edge 和统一执行入口。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 3 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
