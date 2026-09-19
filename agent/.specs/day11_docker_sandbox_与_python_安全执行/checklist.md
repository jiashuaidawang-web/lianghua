# Day 11 Checklist

- [x] Function works on the happy path.  SANDBOX Task 经 Handler → Service → FakeExecutor 返回 `[SANDBOX OK] exitCode=0`。
- [x] Error path is covered.  三种失败（超时 / 策略拒绝 / 基础设施错误）均有分支测试；Handler 异常兜底不上抛。
- [x] State/DTO/Tool result is validated.  SandboxResult.ok()/timedOut()/securityRejected() 显式区分；RESULTS 写入格式化文本。
- [x] No real credentials committed.  沙盒镜像名走 `quant.sandbox.image` 配置，默认 `python:3.11-slim`，无凭证。
- [x] No beta/snapshot dependency added.  DockerSandboxExecutor 通过 ProcessBuilder 调 docker CLI，零新 Maven 依赖。
- [x] Unit tests pass.  `mvn -o test` → BUILD SUCCESS。
- [x] Manual verification completed where applicable.  全量回归通过。

## 测试结果（回归证据）

```
Tests run: 133, Failures: 0, Errors: 0, Skipped: 0   BUILD SUCCESS
```

新增测试类：
- `SandboxPolicyTest`（14）：空/超长脚本、纯计算通过、os.system/subprocess/eval/exec/socket/urllib/requests/危险路径/大小写绕过、Limits 校验。
- `SandboxServiceTest`（10）：正常、策略拒绝、空脚本、超时、基础设施错误、语言错误、配额缺省、stdout 回显、Result 状态语义。
- `SandboxTaskHandlerTest`（8）：type、正常、缺 script、危险脚本、配额解析、非法配额回退、异常兜底、默认语言、target/params 访问。

历史 Day 1~10 测试（99 → 因新增 34 个 Day 11 测试，合计 133）全部通过，无退化。

## 设计决策备注

- **语言校验放在 SandboxService 而非 DockerSandboxExecutor**：语言支持是"应用层策略"，不应绑定到 Docker 实现。这样换一个非 Docker 的执行器（如进程内沙盒），语言策略仍然生效；且 Fake 单测能覆盖语言错误分支。
- **`call(` 单独一条正则**：若与 `os.system` 等合并在 `\b(...)\b` 组里，尾随 `\b` 在 `(` 之后会失败（`(` 后常跟 `[`/`'`/空格，均非单词字符）。改为只要求前导 `\b` 的 `\bcall\(`。
- **网络正则改为整词匹配 `\b(socket|urllib|requests|http\.client|httpx)\b`**：覆盖 `import socket` 等裸导入，而非仅 `socket.` 用法。

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
