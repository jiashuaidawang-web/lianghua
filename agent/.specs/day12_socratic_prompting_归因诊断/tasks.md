# Day 12 Tasks

- [x] Confirm current API from official docs/Javadoc.
- [x] Implement domain contract (DiagnosisCategory, Likelihood, Hypothesis, Diagnosis).
- [x] Implement Strategy pattern (DiagnosisStrategy + 5 strategies).
- [x] Implement DiagnosisService decision-tree orchestrator.
- [x] Implement DiagnosisNode.
- [x] Extend StateKeys + QuantAgentState accessor for DIAGNOSIS.
- [x] Extend RenderNode to render Diagnosis when present.
- [x] Wire graph topology (DIAGNOSIS node + conditional edge in compileDay5).
- [x] Register beans in AiServicesConfiguration.
- [x] Add normal-path JUnit 5 test (DiagnosisServiceTest, DiagnosisNodeTest happy path).
- [x] Add error/boundary JUnit 5 test (all 6 decision-tree rules + severity priority).
- [x] Run `mvn -o test` → BUILD SUCCESS, Day1~Day11 no regression.
- [x] Update checklist with observed results.

## Vertical Evolution Contract

### Capability
Socratic Diagnosis Capability

### Before
Day 11 已有可安全执行的因子环境；异常时只能盲目重规划或暴露原始错误。

### After
Agent 遇到异常时会进入可解释的诊断路径（证据 → 归因 → 推荐动作），而不是直接修改策略。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 11 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
