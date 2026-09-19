# Day 11 Plan

1. Read constitution.md and existing implementation.
2. Define domain/application/infrastructure boundaries.
3. Implement the smallest vertical slice for: Docker Sandbox 与 Python 安全执行.
4. Add unit tests with deterministic fixtures (FakeSandboxExecutor).
5. Update docs/checklist and verify build.

## 包结构与接口边界

```
com.quant.agent
├── domain/sandbox
│   ├── SandboxLimits.java       值对象：资源配额（DEFAULT + 带校验工厂）
│   └── SandboxResult.java       值对象：执行结果（三失败 + Serializable）
├── application/sandbox
│   ├── SandboxPolicy.java       脚本安检门（黑名单 + 长度上限）
│   └── SandboxService.java      应用层编排：语言校验 → 策略 → 执行 → 格式化
├── infrastructure/sandbox
│   ├── SandboxExecutor.java     接口（依赖倒置）
│   ├── DockerSandboxExecutor.java   docker run 实现（零新依赖）
│   └── SandboxProperties.java   quant.sandbox.* 配置
└── graph/handlers
    └── SandboxTaskHandler.java   SANDBOX TaskHandler（自动注册 ExecutorNode）
```

### 数据流
```
Planner 生成 SANDBOX Task(params:{script,language,inputData,limits})
  → ExecutorNode → SandboxTaskHandler.handle()
    → SandboxService.run()
      → [1] 语言校验（仅 python）
      → [2] SandboxPolicy.validate()（确定性黑名单）
      → [3] SandboxExecutor.execute()（DockerSandboxExecutor）
      → [4] 格式化为文本 → 写入 State.RESULTS
        → ReviewNode → RenderNode/END
```

### 安全边界
- 进程：脚本内容来自 LLM → 视为不可信输入 → 先 Java 校验再起容器。
- 网络：默认 `--network none`；仅当 `limits.networkAllowed=true` 时启用默认网。
- 文件系统：`--read-only` + `--tmpfs /tmp:rw,noexec,nosuid`；脚本以只读 `-v ...:ro` 挂载。
- 资源：`--memory` / `--cpus` / `--pids-limit` / `--cap-drop ALL` / `--security-opt=no-new-privileges`。
- 超时：Java `Process.waitFor(timeoutMs)` + `destroyForcibly()`（硬超时，不依赖 docker stop）。
- 输出：截断到 `maxOutputBytes`（防内存炸）。

## 官方 API / 版本
- LangChain4j: 1.20.0
- LangGraph4j: 1.8.26
- Spring Boot: 4.1.1
- Java: 17
- Docker CLI：运行环境提供（不在 Maven 依赖中）。

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
