# Day 9 Spec — MCP Server 与标准工具暴露

## Goal
MCP Server、Resources/Tools/Prompts、LangChain4j MCP 模块与 LangGraph4j 接入边界。

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
MCP Capability

### Before
Day 8 Tool 只通过本地 Tool registry 调用。

### After
同一工具能力可通过 MCP 标准协议被外部 Agent/Host 发现和调用。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 8 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
