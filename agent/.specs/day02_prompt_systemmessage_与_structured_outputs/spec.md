# Day 2 Spec — Prompt、SystemMessage 与 Structured Outputs

## Goal
SystemMessage、UserMessage、AiServices、POJO/record、JSON Schema/Prompt fallback；理解“自然语言协议→强类型 DTO”。

## Scope
- In scope: learning + implementation in `/lianghua/agent`.
- Out of scope: unrelated infrastructure refactors.

## Contract
- Inputs, outputs, errors and observability must be explicit.
- Untrusted model output is validated before side effects.

## Definition of Done
See checklist.md.

## 演进任务 E1：原生 Structured Output

将当前"prompt 文字约束 + retry 兜底"升级为 LangChain4j 1.20 原生 `ResponseFormat + JsonSchema`，让模型 API 强制输出符合 Schema 的 JSON。详见 tasks.md E1。

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
