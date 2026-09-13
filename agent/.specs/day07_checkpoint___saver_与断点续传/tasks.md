# Day 7 Tasks

- [x] Confirm current API from official docs/Javadoc.
- [x] Implement domain contract.
- [x] Implement infrastructure adapter/node/tool.
- [x] Wire the application path.
- [x] Add normal-path JUnit 5 test.
- [x] Add error/boundary JUnit 5 test.
- [x] Run `mvn test`.
- [x] Update README/checklist with observed results.

## 实现说明

| Task | 实际落点 |
|------|---------|
| Confirm API | LangGraph4j 1.8.26 内置 `BaseCheckpointSaver` / `MemorySaver` / `FileSystemSaver` / `CompileConfig` / `RunnableConfig` |
| domain contract | 无需自定义 Checkpoint 类，用框架内置 `org.bsc.langgraph4j.checkpoint.Checkpoint` |
| adapter/node/tool | `QuantAgentStateGraph.compileDay5(BaseCheckpointSaver)` 接入框架 Checkpoint；`GraphRunner.runDay5(symbol, saver)` + `resumeDay5(threadId, saver)` |
| application path | 无新增 HTTP 接口，只内部加能力（Controller 层 Day 7 不改） |
| normal-path test | `CheckpointTest.memorySaverShouldSaveAndLoad` + `graphExecutionShouldAutoSaveCheckpoints` |
| error/boundary test | `CheckpointTest.shouldResumeFromCheckpoint`（恢复续传） |
| mvn test | 41 tests, BUILD SUCCESS |
| docs/checklist | checklist.md + tasks.md 更新 |

## 核心改动（2 处生产代码）

1. **`QuantAgentStateGraph.java`**：新增 `compileDay5(BaseCheckpointSaver)` —— 通过 `CompileConfig.builder().checkpointSaver(saver).build()` 注入 saver，框架自动每节点存快照；保留无参 `compileDay5()` 兼容旧调用
2. **`GraphRunner.java`**：`runDay5(symbol, saver)` 注入 saver + 生成 threadId（`day5-{symbol}-{uuid}`）；新增 `resumeDay5(threadId, saver)` 恢复入口

## 关键发现

LangGraph4j 1.8.26 **内置完整 Checkpoint 支持**，无需自定义实现：
- `BaseCheckpointSaver` —— 接口（list/get/put/release）
- `MemorySaver` —— 内存实现（测试用）
- `FileSystemSaver` —— 文件实现（单机）
- `CompileConfig` —— 编译时注入 saver
- `RunnableConfig` —— 运行时带 threadId/checkPointId

## Vertical Evolution Contract

### Capability
Durable Execution Capability

### Before
Day 6 的图只能依赖进程内内存 State。

### After
Agent 可以保存/恢复执行上下文，并在进程重启后继续。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 6 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
