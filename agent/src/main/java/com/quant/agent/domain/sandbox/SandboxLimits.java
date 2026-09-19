package com.quant.agent.domain.sandbox;

// ============================================================================================
// 【Day 11 · 阅读入口】SandboxLimits —— 沙盒执行的"资源配额单"，Day 11 安全边界的量化表达。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 11 域模型的"配置契约"。由 SandboxService 传给 DockerSandboxExecutor。
//   建议阅读时机：Day 11 最先读它（1 分钟）。
//   学完能回答：
//     1. 为什么用 record 而不是 Builder？
//     2. 这些限制分别对应 Docker 的哪个 flag？
//
//   💡 为什么用 record？
//     SandboxLimits 是"值对象"：创建后不可变、按值比较、没有行为。
//     record 正好表达这种"纯数据 + 不可变"语义，比 Builder 简洁。
//     而且它是从外部传入的配置，不需要一步步 build。
//
//   💡 各字段对应的 Docker flag：
//     timeoutMs      → Java 层 Process.waitFor(timeout) + destroyForcibly()（也受 docker 内超时约束）
//     memoryMb       → --memory {m}m
//     cpu            → --cpus {n}
//     tmpMb          → --tmpfs /tmp:rw,noexec,nosuid,size={m}m
//     pidsLimit      → --pids-limit {n}（防 fork 炸弹）
//     networkAllowed → true=默认网；false=--network none
//     maxOutputBytes → 输出截断上限（防内存炸）
//
//   ⬇ 下一步：看 SandboxResult（沙盒执行后的"结果报告"）。
// ============================================================================================

/**
 * 沙盒执行的资源配额。
 *
 * <p>值对象（record），不可变。定义一次执行的边界：
 * 超时、内存、CPU、临时空间、进程数、网络策略、输出上限。
 */
public record SandboxLimits(
        long timeoutMs,
        int memoryMb,
        double cpu,
        int tmpMb,
        int pidsLimit,
        boolean networkAllowed,
        int maxOutputBytes) {

    /** 默认配额：30 秒 / 256MB 内存 / 1 CPU / 64MB tmpfs / 64 进程 / 无网络 / 64KB 输出。 */
    public static final SandboxLimits DEFAULT = new SandboxLimits(
            30_000L, 256, 1.0, 64, 64, false, 65_536);

    /**
     * 带校验的工厂：任何数值越界都快速失败，避免把非法配额传给 Docker。
     */
    public static SandboxLimits of(long timeoutMs, int memoryMb, double cpu,
                                    int tmpMb, int pidsLimit, boolean networkAllowed,
                                    int maxOutputBytes) {
        if (timeoutMs <= 0) throw new IllegalArgumentException("timeoutMs must be > 0");
        if (memoryMb <= 0) throw new IllegalArgumentException("memoryMb must be > 0");
        if (cpu <= 0) throw new IllegalArgumentException("cpu must be > 0");
        if (tmpMb <= 0) throw new IllegalArgumentException("tmpMb must be > 0");
        if (pidsLimit <= 0) throw new IllegalArgumentException("pidsLimit must be > 0");
        if (maxOutputBytes <= 0) throw new IllegalArgumentException("maxOutputBytes must be > 0");
        return new SandboxLimits(timeoutMs, memoryMb, cpu, tmpMb, pidsLimit, networkAllowed, maxOutputBytes);
    }
}
