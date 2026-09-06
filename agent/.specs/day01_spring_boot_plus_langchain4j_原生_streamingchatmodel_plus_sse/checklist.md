# Day 1 Checklist

- [x] Function works on the happy path.
- [x] Error path is covered.
- [x] State/DTO/Tool result is validated.
- [ ] No real credentials committed.
- [x] No beta/snapshot dependency added.
- [x] Unit tests pass.
- [x] Manual verification completed where applicable.

## 测试证据（2026-09-06）

```
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `shouldStreamPartialResponses`：正常流式路径通过，验证 onPartialResponse × 2 + onCompleteResponse
- `shouldPropagateErrors`：错误传播路径通过，验证 onError → sink.error

## 生产代码可观测性输出（shouldStreamPartialResponses 运行中观察）

```
完整的文本:你好
token 用量:TokenUsage { inputTokenCount = 10, outputTokenCount = 20, totalTokenCount = 30 }
为什么停:STOP
```

## 修复记录

- **问题**：`onCompleteResponse` 调用 `completeResponse.aiMessage().text()`，但测试 fixture 传 `null` → NPE
- **根因**：生产代码增强可观测性后，测试 fixture 未同步更新
- **修复**：更新 `shouldStreamPartialResponses` fixture，提供合法 `ChatResponse` mock（aiMessage/tokenUsage/finishReason）
- **生产代码**：零修改（生产环境 onCompleteResponse 永远收到非 null 响应，无需防御性判空）

## ⚠️ 未解决风险

- `application.yml` 中存在硬编码 API Key（`ak_2n14Hu0Um6ge3nj40d6zL5fl6Kr2s`），违反 constitution「真实 API Key 必须通过环境变量注入，禁止进入源码或 Git」。建议后续迁移为 `${QUANT_LLM_API_KEY}` 环境变量占位符。

## Vertical Evolution Contract

### Capability
LLM Streaming Capability

### Before
项目仅有基础 Spring Boot 工程/LLM adapter 骨架。

### After
系统具备统一 LLM Streaming 能力，可持续向前端推送模型增量输出。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 0 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
