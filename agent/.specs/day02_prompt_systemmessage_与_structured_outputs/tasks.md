# Day 2 Tasks

- [x] Confirm current API from official docs/Javadoc.
- [x] Implement domain contract.
- [x] Implement infrastructure adapter/node/tool.
- [x] Wire the application path.
- [x] Add normal-path JUnit 5 test.
- [x] Add error/boundary JUnit 5 test.
- [x] Run `mvn test`.
- [x] Update README/checklist with observed results.

## Vertical Evolution Contract

### Capability
Structured Output Capability

### Before
Day 1 已能调用 LLM 并流式输出纯文本。

### After
同一 LLM 能按契约输出结构化分析结果，并在非法输出时进入明确失败路径。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 1 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
