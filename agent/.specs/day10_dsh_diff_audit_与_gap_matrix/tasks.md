# Day 10 Tasks

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
DSH Diff Audit Capability

### Before
Day 1~9 已提供 LLM、State、Planner、Tools、MCP 等基础能力。

### After
Agent 能在正式执行前识别“需求 vs 现有能力”的落差，并形成结构化审计结果。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 9 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
