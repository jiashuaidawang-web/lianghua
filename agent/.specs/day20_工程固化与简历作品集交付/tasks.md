# Day 20 Tasks

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
Production Release & Portfolio

### Before
Day 19 已有功能完整且通过 E2E/鲁棒性验证。

### After
形成可运行、可审计、可解释、可恢复、可展示的量化投研 Agent Release。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 19 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
