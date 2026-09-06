# Lianghua Agent — 20 天双轮驱动版

> **Learn 极速认知 + Engineering Evolution + Spec Driven Development**

## GA 技术基线

- Java 17
- Spring Boot 4.1.1
- LangChain4j 1.20.0
- LangGraph4j 1.8.26
- 不使用 Spring AI
- 不使用 LangChain4j 0.x
- 不使用 LangGraph4j beta/snapshot

## 最重要的设计

20 天不是 20 个 Demo，而是同一个 `/lianghua/agent` 的 20 次 Capability 演进。

```text
Learn Prompt
  → Learning Gate
  → Engineering Evolution Prompt
  → constitution
  → Spec / Plan / Tasks
  → Code
  → Test / Regression
  → Checklist
  → Git Commit
  → Capability +1
```

## 双 Prompt 分工

### `/learn`：认知轮

`/learn/prompts/dayXX-learning.md` 负责让你本人理解：What / Why / Runtime / Java Mental Model / Debug / 口头解释。

### `/agent`：工程轮

`/agent/prompts/dayXX-engineering.md` 负责让 Claude/Cursor 读取历史工程、复用已有能力、只增加今天一个 Capability，并执行 Regression。

### `.specs`：设计记忆

每一天固定维护 `spec.md / plan.md / tasks.md / checklist.md`，记录可追溯设计与验收证据。

## 20 天能力演进

01. LLM Streaming Capability
02. Structured Output Capability
03. Tool Calling Capability
04. Agent State & Graph Runtime
05. Planner Capability
06. Re-planning Capability
07. Durable Execution Capability
08. Finance Data Tooling
09. MCP Capability
10. DSH Diff Audit Capability
11. Sandboxed Execution Capability
12. Socratic Diagnosis Capability
13. Interactive Action Contract
14. Human-in-the-Loop Capability
15. Research RAG Capability
16. Multi-Agent Collaboration
17. Realtime Agent Gateway
18. Backtest Evaluation Capability
19. Production E2E & Resilience
20. Production Release & Portfolio

## 每天怎么做

请先阅读根目录 `EXECUTION_PROTOCOL.md`，然后按 Day README → Learn Prompt → Engineering Prompt → Spec → Test → Checklist → Git 的顺序执行。
