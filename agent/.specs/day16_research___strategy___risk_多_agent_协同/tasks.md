# Day 16 Tasks

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
Multi-Agent Collaboration

### Before
Day 15 已有 RAG 研究上下文与 HITL。

### After
形成有职责边界的多 Agent 协同闭环，Risk 成为独立的确定性闸门。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 15 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
