package com.quant.agent.infrastructure.sandbox;

import com.quant.agent.domain.sandbox.SandboxLimits;
import com.quant.agent.domain.sandbox.SandboxResult;

import java.util.Map;
import java.util.function.Function;

// ============================================================================================
// 【Day 11 · 测试工具】FakeSandboxExecutor —— 不启动容器的确定性沙盒替身。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：测试专用的 SandboxExecutor 实现。
//   学完能回答：为什么测试不能直接用 DockerSandboxExecutor？
//
//   💡 为什么不用真的？
//     - 测试环境可能没 Docker → 测试会随机失败（环境依赖）
//     - 起容器要秒级 → 测试变慢
//     - 容器行为不确定（网络/镜像）→ 测试不稳定
//   用 Fake：毫秒级、确定性、无环境依赖。
// ============================================================================================

/**
 * 测试用沙盒执行器：按脚本内容返回预设结果，不起容器。
 */
public class FakeSandboxExecutor implements SandboxExecutor {

    /** 把脚本映射到结果；脚本不匹配任何 key → 用 defaultResult。 */
    private final Function<String, SandboxResult> mapper;

    public FakeSandboxExecutor(Function<String, SandboxResult> mapper) {
        this.mapper = mapper;
    }

    /**
     * 构造一个"全部成功"的 Fake。
     */
    public static FakeSandboxExecutor alwaysSuccess() {
        return new FakeSandboxExecutor(script ->
                SandboxResult.success(0, "ok: " + script.hashCode(), "", 5L, false));
    }

    /**
     * 构造一个"全部超时"的 Fake。
     */
    public static FakeSandboxExecutor alwaysTimeout() {
        return new FakeSandboxExecutor(script ->
                SandboxResult.timedOut("", "slow", 30_000L, false));
    }

    /**
     * 构造一个"全部基础设施错误"的 Fake（模拟 Docker 不可用）。
     */
    public static FakeSandboxExecutor alwaysError() {
        return new FakeSandboxExecutor(script ->
                SandboxResult.infrastructureError("Docker not available"));
    }

    @Override
    public SandboxResult execute(SandboxLimits limits, String script, String language,
                                  Map<String, Object> inputData) {
        return mapper.apply(script);
    }
}
