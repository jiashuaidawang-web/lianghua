# Day 11 Spec — Docker Sandbox 与 Python 安全执行

## Goal
把 LLM 生成的 Python 因子代码放进隔离执行环境，施加超时 / 资源 / 网络 / 文件系统限制；违规 / 超时可被终止并审计。

## Scope
- In scope: learning + implementation in `/lianghua/agent`。
- Out of scope: unrelated infrastructure refactors。

## Contract
- Inputs, outputs, errors and observability must be explicit.
- Untrusted model output (LLM 生成的脚本) is validated before side effects.
- 失败必须可审计：超时 / 策略拒绝 / 基础设施错误 三种失败明确区分。

## 设计边界（纵向演进）

### 新增最小元素
- `domain/sandbox/SandboxLimits.java` —— 资源配额值对象（超时 / 内存 / CPU / tmpfs / pids / 网络 / 输出上限），含 `DEFAULT` 与带校验的 `of(...)` 工厂。
- `domain/sandbox/SandboxResult.java` —— 执行结果值对象，三种互斥失败（timedOut / securityRejected / infrastructureError），`Serializable`。
- `application/sandbox/SandboxPolicy.java` —— 脚本"安检门"：确定性黑名单 + 长度上限，在容器启动前拦截。
- `application/sandbox/SandboxService.java` —— 应用层编排：语言校验 → 策略校验 → 执行 → 结果格式化。
- `infrastructure/sandbox/SandboxExecutor.java` —— 沙盒执行器接口（依赖倒置，便于 Fake 单测）。
- `infrastructure/sandbox/DockerSandboxExecutor.java` —— 基于 `docker run` 的零新依赖实现。
- `infrastructure/sandbox/SandboxProperties.java` —— `quant.sandbox.*` 配置（镜像名）。
- `graph/handlers/SandboxTaskHandler.java` —— SANDBOX 类型 Task 的 Handler，自动注册进 ExecutorNode。

### 复用（不新建第二套 Runtime）
- `QuantAgentState` / `StateKeys`：沙盒结果写入既有 `RESULTS` key。
- `ExecutorNode` 的 `TaskHandler` 注册表：`SandboxTaskHandler` 作为新 `@Component` 自动注入。
- `DiffAuditService.matchCapability` + `CapabilityType.SANDBOX`：审计识别沙盒能力。
- `AiServicesConfiguration` 装配车间：注册 SandboxExecutor / SandboxService / SandboxTaskHandler / SandboxProperties。
- `PlannerAiService` SYSTEM_PROMPT：扩展为 8 种 TaskType（新增 SANDBOX）。

### 安全边界（纵深防御，两层）
1. Java 层（`SandboxPolicy`）：确定性黑名单 + 长度上限，命中即拒绝，省得起容器。
2. 容器层（`DockerSandboxExecutor`）：`--network none` / `--read-only` / `--tmpfs /tmp` / `--memory` / `--cpus` / `--pids-limit` / `--cap-drop ALL` / `--security-opt=no-new-privileges` / `--rm`；Java 层 `Process.waitFor(timeout)` 硬超时 + `destroyForcibly()`；输出截断到 `maxOutputBytes`。

## 插入点
- 图拓扑：无改动。`SandboxTaskHandler` 自动注册进 `ExecutorNode`，`RESULTS` 直接容纳沙盒结果文本。
- Controller：无新增端点（复用 `/api/v1/planner/plan`）。

## 测试策略
- `FakeSandboxExecutor`：不启动容器，按脚本返回预设结果（成功 / 超时 / 错误）。
- 单元测试：SandboxPolicy（黑名单 + 长度 + 大小写）、SandboxService（正常 / 策略拒绝 / 超时 / 语言错误 / 配额缺省）、SandboxTaskHandler（正常 / 缺 script / 危险脚本 / 配额解析 / 异常兜底）。
- 回归：全量 `mvn -o test`，Day 1~10 历史测试不退化。

## Definition of Done
见 checklist.md。

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
