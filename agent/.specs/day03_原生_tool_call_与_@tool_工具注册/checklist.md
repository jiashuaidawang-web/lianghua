# Day 3 Checklist

- [ ] Function works on the happy path.
- [ ] Error path is covered.
- [ ] State/DTO/Tool result is validated.
- [ ] No real credentials committed.
- [ ] No beta/snapshot dependency added.
- [ ] Unit tests pass.
- [ ] Manual verification completed where applicable.

## Vertical Evolution Contract

### Capability
Tool Calling Capability

### Before
Day 1~2 只有 LLM 文本/结构化输出能力。

### After
Agent 可以通过注册的 Java Tool 获取外部数据，不允许模型直接产生副作用。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 2 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
