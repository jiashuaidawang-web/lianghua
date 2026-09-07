# Day 2 Tasks

- [x] Confirm current API from official docs/Javadoc.
- [x] Implement domain contract.
- [x] Implement infrastructure adapter/node/tool.
- [x] Wire the application path.
- [x] Add normal-path JUnit 5 test.
- [x] Add error/boundary JUnit 5 test.
- [x] Run `mvn test`.
- [x] Update README/checklist with observed results.

## 演进任务 E1：从 Prompt 约束升级到原生 Structured Output

### 问题
当前 `StockAnalysisAiService` 通过 SYSTEM_PROMPT 文字告诉 LLM "请按 JSON 格式返回"，属于**软约束**：
- LLM 仍做自由文本生成，可能返回纯文本、多余文字、字段名拼错
- 必须靠 `StructuredAnalysisService` 的 retry 兜底（当前 MAX_RETRY=2）
- 准确率低于 Spring AI `.entity()` 或 API 级 `response_format`

### 目标
升级为 LangChain4j 1.20 原生 **ResponseFormat + JsonSchema**，让模型 API **强制**输出符合 Schema 的 JSON：
- 结构错误由 API 层保证，基本消除"LLM 不遵守格式"导致的重试
- retry 只用于处理业务校验失败（如 score 超范围），不再处理结构错误

### 改动范围
1. `AiServicesConfiguration`：配置 `ResponseFormat` + `JsonSchema`（从 `StockAnalysis` record 生成）
2. `StockAnalysisAiService`：精简 SYSTEM_PROMPT（不再需要重复 JSON Schema 描述）
3. `StructuredAnalysisService`：保留 retry，但重试原因从"结构非法"变为"业务校验非法"
4. 测试：更新 `Day02StructuredAnalysisServiceTest`，验证新路径

### 验收标准
- [ ] LLM 输出结构由 API 保证，不再依赖 prompt 文字约束
- [ ] 合法请求 0 次重试即可成功
- [ ] 旧测试全部通过（Regression）
- [ ] 新增测试覆盖"API 强制 Schema"路径

### 参考 API（需验证官方文档）
- `dev.langchain4j.model.chat.request.ResponseFormat`
- `dev.langchain4j.model.chat.request.json.JsonSchema`
- `AiServices.builder().responseFormat(...)` 或 `ChatModel` 配置

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
