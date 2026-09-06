# Day 10 Checklist

- [ ] Function works on the happy path.
- [ ] Error path is covered.
- [ ] State/DTO/Tool result is validated.
- [ ] No real credentials committed.
- [ ] No beta/snapshot dependency added.
- [ ] Unit tests pass.
- [ ] Manual verification completed where applicable.

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
