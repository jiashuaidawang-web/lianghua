# LangChain4j 1.20.0 AiService 返回 List\<POJO\> 排坑纪实：PojoCollectionOutputParser 抛 IllegalStateException

## 一、现象

写了一个基于 LangChain4j 的声明式 AiService 代理接口，方法签名返回 `List<Task>`，调用时报错：

```
规划异常，准备重试: error=null, attempt=1/2
```

几个关键特征：

- **每次调用都是 `error=null`**：`e.getMessage()` 返回 null，看不到任何错误信息
- **20ms 就失败**：根本不是网络超时（LLM 调用至少几百毫秒），说明请求没发出去
- **重试一定次数后彻底失败**：重试逻辑在反复踩同一个坑

接口定义长这样：

```java
public interface PlannerAiService {
    @UserMessage(SYSTEM_PROMPT + "\n\n请分析以下用户请求并生成 Task 列表：\n{{userRequest}}")
    List<Task> plan(@V("userRequest") String userRequest);
}
```

## 二、第一次排查：怀疑错了方向

看到 20ms 失败，先入为主地怀疑是**配置问题**——比如 `ChatModel` Bean 上硬编码了某个 `responseFormat`（JSON Schema），导致 planner 返回的 `List<Task>` 被强制扭曲成别的类型。

于是给 planner 单独新建了一个不带 `responseFormat` 的 `plainChatLanguageModel` Bean，编译、重启、重试——**现象完全不变**。

这说明方向错了。真正的问题和 responseFormat 无关。

## 三、踩坑关键：日志里 e.getMessage() 为 null

之前的 catch 块只打了 `e.getMessage()`：

```java
log.warn("规划异常，准备重试: error={}, attempt={}/{}", e.getMessage(), attempt, MAX_RETRY);
```

而**这个异常的 `getMessage()` 就是 null**，等于什么都没打出来。

改成打完整异常对象（含类名 + 堆栈）之后，终于看到了真凶：

```java
// 把 e.getMessage() 改成 e，并作为最后一个参数传入堆栈
log.warn("规划异常，准备重试: error={}, attempt={}/{}", e, attempt, MAX_RETRY, e);
```

## 四、真正的根因

新的堆栈一出来就清楚了：

```
java.lang.IllegalStateException
    at dev.langchain4j.service.output.PojoCollectionOutputParser.formatInstructions(PojoCollectionOutputParser.java:70)
    at dev.langchain4j.service.output.ServiceOutputParser.outputFormatInstructions(ServiceOutputParser.java:112)
    at dev.langchain4j.service.DefaultAiServices$1.appendOutputFormatInstructions(DefaultAiServices.java:1349)
    at dev.langchain4j.service.DefaultAiServices$1.invoke(DefaultAiServices.java:655)
```

**异常发生在 `appendOutputFormatInstructions` 阶段**——也就是框架在**组装 prompt**、生成"输出格式指令"的时候。这时候还没发出 LLM 请求，所以 20ms 就失败。

### 为什么是这个异常？

把 `langchain4j-1.20.0.jar` 解压，反编译 `PojoCollectionOutputParser`：

```java
public String formatInstructions() {
    throw new IllegalStateException();   // ← 就这一行，空桩
}
```

**LangChain4j 1.20.0 的 `PojoCollectionOutputParser.formatInstructions()` 是一个直接抛 `IllegalStateException` 的空桩——根本没实现。**

### 触发条件

LangChain4j 的 `ServiceOutputParser` 会根据 AiService 方法的**返回类型**选择不同的解析器：

| 返回类型 | 走的解析器 | 1.20.0 是否正常 |
|---------|-----------|:--------------:|
| 单 POJO（如 `StockAnalysis`） | `PojoOutputParser` | ✅ 正常 |
| `List<POJO>`（如 `List<Task>`） | `PojoCollectionOutputParser` | ❌ `formatInstructions()` 是空桩，直接抛异常 |

所以同样的工程里，Day 2 返回单 POJO 的 `StockAnalysis` 接口能用，Day 5 返回 `List<Task>` 的 planner 接口必挂。

## 五、修复方案

核心思路：**让框架别走 `PojoCollectionOutputParser`。** 把 `List<Task>` 包成一个单 POJO，框架就会改用正常工作的 `PojoOutputParser`。

### 1. 新建包装 POJO

```java
package com.quant.agent.domain.task;

import java.io.Serializable;
import java.util.List;

/**
 * List<Task> 的包装 POJO。
 * LangChain4j 1.20.0 的 PojoCollectionOutputParser 是空桩（抛 IllegalStateException），
 * 把 List<Task> 包成单 POJO 后，框架改用正常工作的 PojoOutputParser。
 */
public record Plan(List<Task> tasks) implements Serializable {
    private static final long serialVersionUID = 1L;
}
```

### 2. 修改 AiService 接口返回类型，并调整 prompt 示例

```java
public interface PlannerAiService {

    String SYSTEM_PROMPT = """
            你是一个任务规划师。
            ...
            输出格式：一个 JSON 对象，包含 "tasks" 数组，数组每个元素是一个 Task：
            {
              "tasks": [
                { "type": "ANALYSIS", "target": "股票代码" },
                { "type": "DATA_FETCH", "target": "股票代码", "params": {"fields": ["price", "pe"]} },
                { "type": "REPORT", "params": {"format": "summary"} }
              ]
            }
            ...
            """;

    // ⚠ 不能直接返回 List<Task>，包成 Plan 走单对象解析
    @UserMessage(SYSTEM_PROMPT + "\n\n请分析以下用户请求并生成 Task 列表：\n{{userRequest}}")
    Plan plan(@V("userRequest") String userRequest);
}
```

### 3. 调用方拆开包装

```java
// 之前：List<Task> tasks = plannerAiService.plan(enhancedRequest);
// 之后：
Plan plan = plannerAiService.plan(enhancedRequest);
List<Task> tasks = plan.tasks();

// 后续的 target 兜底、校验、重试逻辑完全不用动
tasks = fillTargetIfNeeded(tasks, extractedCode);
if (isValidPlan(tasks)) {
    return tasks;
}
```

编译通过，重启后请求正常返回 Task 列表，修复生效。

## 六、复盘：几个教训

### 1. catch 块里别只打 `e.getMessage()`

很多异常（特别是框架内部异常、`IllegalStateException`、`NullPointerException`）的 `getMessage()` 就是 null。正确写法：

```java
// ❌ 会丢信息
log.warn("异常: {}", e.getMessage(), e);   // 第一个占位符可能是 null
// ✅ 类名 + 堆栈都有
log.warn("异常: {}", e, attempt, e);       // SLF4J 最后一个 Throwable 参数会打堆栈
```

### 2. 20ms 失败 ≈ 根本没发请求

LLM 调用是网络 IO，正常至少几百毫秒。如果几十毫秒就失败，说明异常发生在**本地组装阶段**（prompt 构造、参数校验、输出格式指令生成等），压根没到网络层。这时候别盯着网络/配置看，往框架调用栈上游找。

### 3. "声明式代理"不是魔法，返回类型会决定框架走哪条路径

LangChain4j / Spring AI 这类声明式接口看起来只是加个注解，但框架内部会根据返回类型走不同的解析路径。遇到版本 bug 时，**"加一层包装"把类型从 List\<POJO\> 变成单 POJO，往往比升级版本更安全、改动更小**。

### 4. 怀疑框架之前，先确认改动真的部署了

第一次怀疑 responseFormat 时，改了 Bean 配置，但现象完全不变。后来才意识到要确认：编译产物里是否真的包含了新 Bean、运行中的进程是否加载了新代码。否则就会在"明明改了却没用"上白耗时间。

## 七、环境信息

- LangChain4j：**1.20.0**
- Spring Boot：7.0.9（WebFlux）
- JDK：21

> 注：`PojoCollectionOutputParser` 的空桩问题是 1.20.0 的已知限制，后续版本已修复。如果项目能升级 LangChain4j 版本，升级是最彻底的解法；如果版本被锁死（比如其他模块依赖特定版本），"包一层 POJO"是最小改动的绕过方案。
