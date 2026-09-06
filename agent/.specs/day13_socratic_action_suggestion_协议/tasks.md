# Day 13 Tasks

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
Interactive Action Contract

### Before
Day 12 输出诊断文本，但前端无法稳定识别操作意图。

### After
Agent 输出统一 ActionSuggestion 协议，前端可渲染并提交 action_id。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 12 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
