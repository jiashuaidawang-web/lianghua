package com.quant.agent.infrastructure.sandbox;

import com.quant.agent.domain.sandbox.SandboxLimits;
import com.quant.agent.domain.sandbox.SandboxResult;

import java.util.Map;

// ============================================================================================
// 【Day 11 · 阅读入口】SandboxExecutor —— 沙盒执行的"策略接口"，Day 11 的基础设施契约。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 11 基础设施层的"接口"。夹在 SandboxService（应用层）和
//   具体实现（DockerSandboxExecutor / 测试 Fake）之间。
//   建议阅读时机：Day 11 读完 SandboxResult 后读它。
//   学完能回答：
//     1. 为什么要有这一层接口？直接调 Docker 不行吗？
//     2. 接口为什么只暴露一个 execute 方法？
//     3. 测试时怎么用？
//
//   💡 为什么要有接口？
//     直接调 Docker → SandboxService 和 Docker 耦合 → 无法单测（单测不能起容器）。
//     加一层接口：
//       - 运行时注入 DockerSandboxExecutor（真容器）
//       - 测试时注入 FakeSandboxExecutor（返回 fixture，毫秒级、确定性）
//     这就是"依赖倒置"：应用层依赖抽象，不依赖具体 Docker。
//
//   💡 为什么只有一个 execute？
//     沙盒执行对外就是一件事："给我脚本 + 配额，我还你结果"。
//     参数/返回都用域对象（SandboxLimits / SandboxResult），
//     以后加字段不用改接口签名（开闭原则）。
//
//   💡 测试时怎么用？
//     写一个 FakeSandboxExecutor implements SandboxExecutor，
//     根据输入脚本返回预设结果（成功/超时/异常），
//     就能在不起 Docker 的情况下测 SandboxService 的所有分支。
//
//   ⬇ 下一步：看 DockerSandboxExecutor（真实现）。
// ============================================================================================

/**
 * 沙盒执行器接口。
 *
 * <p>应用层（SandboxService）只依赖此接口，不依赖 Docker。
 * 运行时由 {@link DockerSandboxExecutor} 实现，测试由 Fake 实现。
 */
public interface SandboxExecutor {

    /**
     * 在隔离沙盒中执行脚本。
     *
     * <p>实现必须保证：超时强制终止、资源限制生效、网络按策略隔离、
     * 输出截断到 maxOutputBytes。这些是 Day 11 的安全底线。
     *
     * @param limits     资源配额（超时/内存/CPU/网络/输出上限）
     * @param script     要执行的脚本内容（来自 LLM，视为不可信输入）
     * @param language   脚本语言（如 "python"；本期只支持 python，保留扩展点）
     * @param inputData  传给脚本的输入数据（序列化为 JSON 注入容器）
     * @return 执行结果（成功/超时/基础设施错误）
     */
    SandboxResult execute(SandboxLimits limits, String script, String language, Map<String, Object> inputData);
}
