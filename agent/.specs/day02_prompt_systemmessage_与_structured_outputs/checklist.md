# Day 2 Checklist

- [x] Function works on the happy path.
- [x] Error path is covered.
- [x] State/DTO/Tool result is validated.
- [ ] No real credentials committed.
- [x] No beta/snapshot dependency added.
- [x] Unit tests pass.
- [x] Manual verification completed where applicable.

## 测试证据（2026-09-07）

```
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- Day 1 回归：`QuantLlmServiceTest` 2/2 通过（流式正常路径 + 错误传播）
- Day 2 新增：`StructuredAnalysisServiceTest` 4/4 通过
    - `shouldReturnStructuredResultWhenLlmReturnsValidJson`：合法 DTO 直接返回
    - `shouldThrowAfterRetriesWhenLlmReturnsInvalidJson`：非法 → 重试 2 次 → 明确异常
    - `shouldSucceedAfterRetry`：第 1 次非法，第 2 次合法 → 成功
    - `shouldThrowWhenLlmReturnsNull`：null → 视为非法 → 重试 → 异常

## 工程决策记录

### 测试策略：mock 接口而非底层 ChatModel
- **问题**：直接 mock `ChatModel.chat()` 很脆弱——`AiServices` 是复杂代理框架，内部处理 prompt 模板、输出解析，mock 底层 ChatModel 不被框架正确处理
- **决策**：`StructuredAnalysisService` 改为依赖注入 `StockAnalysisAiService` 接口；测试直接 mock 该接口
- **好处**：测试聚焦「校验逻辑 + 重试策略」，而非框架内部行为；符合「测试行为而非实现」原则

### DTO 校验放在 record 内部
- `StockAnalysis.isValid()` 封装校验逻辑（action 枚举、score 范围、字段非空）
- 符合 constitution「LLM 输出视为不可信输入，校验后再用」

## ⚠️ 未解决风险

- `application.yml` 中存在硬编码 API Key（同 Day 1），建议迁移为 `${QUANT_LLM_API_KEY}` 环境变量占位符。
- `SystemMessage` 目前硬编码在接口中，后续可考虑外部化到配置。

## 演进任务

### E1：原生 Structured Output — 已完成 ✅

**完成时间**：2026-09-07

**改动文件**：
- `infrastructure/llm/LlmConfiguration.java`：新增 `stockAnalysisResponseFormat()`，配置 `ResponseFormat + JsonSchema`，在 `OpenAiChatModel.builder().responseFormat(...)` 应用
- `application/llm/StockAnalysisAiService.java`：精简 SYSTEM_PROMPT，移除 JSON Schema 描述（API 已强制约束）

**升级效果**：

| 维度 | Before（prompt 软约束） | After（API 硬约束） |
|---|---|---|
| 约束方式 | prompt 文字"请按 JSON 格式返回" | `ResponseFormatBuilder().jsonSchema(...)` 下发到模型 API |
| 结构保证 | 无保证，靠 retry 兜底 | API 强制输出符合 Schema 的 JSON |
| 等价于 | —— | Spring AI `.entity(StockAnalysis.class)` |

**测试证据**：
```
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
- Day 1 回归：`Day01QuantLlmServiceTest` 2/2 通过
- Day 2：`Day02StructuredAnalysisServiceTest` 4/4 通过

**关键认知**：
- LangChain4j 1.20 的原生结构化输出 = `ResponseFormat`（指定 JSON 类型 + `JsonSchema`）
- `JsonSchema` 由 `JsonObjectSchema` 构建：`addEnumProperty`、`addStringProperty`、`addNumberProperty`、`required`、`additionalProperties(false)`
- 配置位置：`OpenAiChatModel.builder().responseFormat(...)` —— 在**模型层**而非 AiServices 层

## Vertical Evolution Contract

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
