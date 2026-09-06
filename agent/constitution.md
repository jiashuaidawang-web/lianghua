# Lianghua Agent Constitution

## 1. Version Policy
- Java 17
- Spring Boot 4.1.1
- LangChain4j 1.20.0 GA
- LangGraph4j 1.8.26 stable
- No Spring AI
- No beta/snapshot dependencies unless explicitly isolated in a temporary experiment and never merged into the main path.

## 2. Architecture Rules
- Controller only handles transport concerns.
- Application/service layer owns use cases.
- Provider SDKs stay behind infrastructure adapters.
- LLM output is untrusted input: validate before state mutation or tool execution.
- Risk-sensitive actions require deterministic policy checks and HITL confirmation.
- Tools must be idempotent where practical and must expose explicit descriptions and input constraints.
- Sandbox execution must have timeout, resource, filesystem and network restrictions.

## 3. Agent Rules
- LangGraph4j owns workflow/state orchestration.
- LangChain4j owns LLM/tool/prompt/RAG primitives.
- The LLM must never directly mutate persistence or issue side effects without a registered tool/policy boundary.
- Every interrupt/retry/replan path must have observable state.

## 4. Testing Rules
- Unit tests must not call real LLM endpoints.
- Integration tests may use a configurable provider, but credentials must come from environment variables.
- Reproducible tests should capture deterministic fixtures for model/tool outputs.
- Each Day has a Definition of Done.
