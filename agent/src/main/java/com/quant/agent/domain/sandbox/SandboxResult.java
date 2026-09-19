package com.quant.agent.domain.sandbox;

// ============================================================================================
// 【Day 11 · 阅读入口】SandboxResult —— 沙盒执行的"结果报告"，Day 11 的核心产出。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 11 的"终点产物"。由 SandboxExecutor 产出，写入 State 的 RESULTS。
//   建议阅读时机：读完 SandboxLimits 后读它。
//   学完能回答：
//     1. 为什么区分 timedOut / securityRejected / error？
//     2. 为什么 exitCode = -1 表示"没真正执行"？
//     3. 为什么实现 Serializable？
//
//   💡 为什么区分三种失败？
//     三种失败的"处置方式"不同：
//       timedOut       → 资源问题，可重试（加大超时）
//       securityRejected → 策略问题，不可重试（代码本身违规，重试也没用）
//       error          → 基础设施问题（Docker 不可用等），需人工介入
//     区分后，上游（Handler / ReviewNode / 审计）能做出不同决策。
//     如果只返回一个 boolean success，这些差异就丢了。
//
//   💡 exitCode = -1 表示"没真正执行"？
//     正常进程退出码是 0~255。-1 是"不可能的正常值"，用来标记"进程根本没跑起来"
//     （策略拒绝、Docker 不可用、启动异常）。这样下游一眼能区分"跑了但失败"和"没跑"。
//
//   💡 为什么实现 Serializable？
//     SandboxResult 存入 State（RESULTS 字段），LangGraph4j 的 StateSerializer 会序列化整个 State
//     （为 Day 7 Checkpoint 断点续传做准备）。不可序列化 → 运行时 NotSerializableException。
//     和 Day 5 的 Task、Day 10 的 GapMatrix 同理。
//
//   ⬇ 下一步：看 SandboxExecutor（怎么产出这个结果）。
// ============================================================================================

import java.io.Serializable;

/**
 * 沙盒执行结果。
 *
 * <p>值对象（record），不可变，{@link Serializable}。
 * 三种互斥失败：超时 / 策略拒绝 / 基础设施错误。
 */
public record SandboxResult(
        int exitCode,
        String stdout,
        String stderr,
        boolean timedOut,
        long elapsedMs,
        boolean truncated,
        boolean securityRejected,
        String error) implements Serializable {

    private static final long serialVersionUID = 1L;

    // ====================================================================
    // 工厂方法：每种结果一个语义化入口，避免调用方拼字段出错。
    // ====================================================================

    /** 正常完成。 */
    public static SandboxResult success(int exitCode, String stdout, String stderr,
                                         long elapsedMs, boolean truncated) {
        return new SandboxResult(exitCode, stdout, stderr, false, elapsedMs, truncated, false, null);
    }

    /** 执行超时（Java 层 waitFor 超时后 destroyForcibly）。 */
    public static SandboxResult timedOut(String stdout, String stderr, long elapsedMs, boolean truncated) {
        return new SandboxResult(-1, stdout, stderr, true, elapsedMs, truncated, false,
                "执行超时，已被强制终止");
    }

    /** 策略拒绝：代码命中危险模式，未启动容器。不可重试。 */
    public static SandboxResult securityRejected(String reason) {
        return new SandboxResult(-1, "", "", false, 0L, false, true,
                "安全策略拒绝: " + reason);
    }

    /** 基础设施错误：Docker 不可用、启动异常等。 */
    public static SandboxResult infrastructureError(String message) {
        return new SandboxResult(-1, "", "", false, 0L, false, false,
                "基础设施错误: " + message);
    }

    /** 是否"实质成功"（进程跑完且退出码 0，且未被截断/拒绝）。 */
    public boolean ok() {
        return exitCode == 0 && !timedOut && !securityRejected && error == null;
    }
}
