# Day 11 Tasks

- [x] Confirm current API from official docs/Javadoc.
- [x] Implement domain contract (SandboxLimits, SandboxResult).
- [x] Implement infrastructure adapter/node/tool (SandboxPolicy, SandboxService, SandboxExecutor, DockerSandboxExecutor, SandboxProperties, SandboxTaskHandler).
- [x] Wire the application path (AiServicesConfiguration + TaskType.SANDBOX + CapabilityType.SANDBOX + DiffAuditService + PlannerAiService).
- [x] Add normal-path JUnit 5 test (SandboxServiceTest, SandboxTaskHandlerTest happy path).
- [x] Add error/boundary JUnit 5 test (SandboxPolicyTest 黑名单/长度/大小写; Service 策略拒绝/超时/语言错误/配额缺省; Handler 缺script/危险脚本/配额解析/异常兜底).
- [x] Run `mvn -o test` → BUILD SUCCESS, 133 tests / 0 failures / 0 errors.
- [x] Update README/checklist with observed results.

## Vertical Evolution Contract

### Capability
Sandboxed Execution Capability

### Before
Day 10 可以审计缺口，但不能安全执行生成代码。

### After
Agent 可以在受控沙盒中执行计算任务，违规/超时可被终止并审计。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 10 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
