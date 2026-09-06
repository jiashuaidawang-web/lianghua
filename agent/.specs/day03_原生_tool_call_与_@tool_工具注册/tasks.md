# Day 3 Tasks

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
Tool Calling Capability

### Before
Day 1~2 只有 LLM 文本/结构化输出能力。

### After
Agent 可以通过注册的 Java Tool 获取外部数据，不允许模型直接产生副作用。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 2 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
