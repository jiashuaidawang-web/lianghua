# Day 15 Spec — RAG：研报检索增强与混合检索

## Goal
Document/Embedding/EmbeddingStore/RetrievalAugmentor；优先稳定 API，PGVector/ES 以适配层隔离。

## Scope
- In scope: learning + implementation in `/lianghua/agent`.
- Out of scope: unrelated infrastructure refactors.

## Contract
- Inputs, outputs, errors and observability must be explicit.
- Untrusted model output is validated before side effects.

## Definition of Done
See checklist.md.

## Vertical Evolution Contract

### Capability
Research RAG Capability

### Before
Day 14 Agent 可协同工具和人工。

### After
Research Node 能基于可追溯资料增强上下文，并把证据带入策略决策。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 14 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
