package com.quant.agent.application.sandbox;

import com.quant.agent.domain.sandbox.SandboxLimits;
import com.quant.agent.domain.sandbox.SandboxResult;
import com.quant.agent.infrastructure.sandbox.SandboxExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

// ============================================================================================
// 【Day 11 · 阅读入口】SandboxService —— 沙盒执行的"编排中心"，Day 11 的应用层入口。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 11 的"应用服务"。夹在 SandboxTaskHandler 和 SandboxExecutor 之间。
//     SandboxTaskHandler → SandboxService → [SandboxPolicy + SandboxExecutor]
//   建议阅读时机：读完 SandboxPolicy + SandboxExecutor 后读它。
//   学完能回答：
//     1. 为什么不直接暴露 Executor 给 Handler，非要加这一层？
//     2. 校验和执行的顺序为什么是"先 Policy 后 Executor"？
//     3. 为什么把结果格式化成文本再返回？
//
//   💡 为什么不直接暴露 Executor？
//     Executor 是"基础设施"（知道 Docker、文件、进程）。
//     Handler 是"图节点"（只知道 State）。
//     加一层 Service：
//       - Handler 不依赖 Docker 细节 → 可单测
//       - Service 做"校验 + 执行 + 结果格式化"的编排 → 单一职责
//     符合 constitution 的"Application/service layer owns use cases"。
//
//   💡 为什么先 Policy 后 Executor？
//     性能 + 安全：
//       - Policy 校验是纯 CPU 操作（毫秒级），命中就直接拒绝 → 省得起容器（秒级）
//       - 如果先起容器再校验 → 浪费资源，且容器已启动 = 攻击面已暴露
//     所以：先过"便宜的安检门"，再进"贵的隔离间"。
//
//   💡 为什么格式化成文本？
//     State 的 RESULTS 是 Map<String, String>（key=TaskType, value=文本）。
//     SandboxResult 是强类型 record → 必须序列化成文本才能塞进 RESULTS。
//     格式化后，下游的 ReviewNode / RenderNode / 审计都能直接消费。
//     注意：这里用 toString 而非 JSON，是为了和现有 RESULTS 的"文本"风格一致。
//
//   ⬇ 下一步：看 SandboxTaskHandler（图的节点，调这个 service）。
// ============================================================================================

/**
 * 沙盒执行应用服务：编排"策略校验 → 执行 → 结果格式化"。
 *
 * <p>是 Day 11 沙盒能力的唯一应用层入口。Handler 调它，它调 Policy + Executor。
 */
@Service
public class SandboxService {

    private static final Logger log = LoggerFactory.getLogger(SandboxService.class);

    private final SandboxExecutor executor;

    public SandboxService(SandboxExecutor executor) {
        this.executor = executor;
    }

    /**
     * 在沙盒中执行脚本。
     *
     * <p>流程：
     * <ol>
     *   <li>策略校验（{@link SandboxPolicy#validate}）→ 命中则返回 securityRejected</li>
     *   <li>调 {@link SandboxExecutor#execute} → 真隔离执行</li>
     *   <li>把 {@link SandboxResult} 格式化为文本（写入 State.RESULTS）</li>
     * </ol>
     *
     * @param script   脚本内容（来自 LLM，不可信）
     * @param language 脚本语言（本期仅 python）
     * @param inputData 输入数据
     * @param limits   资源配额（null 则用 {@link SandboxLimits#DEFAULT}）
     * @return 执行结果文本（成功/超时/拒绝/错误）
     */
    public String run(String script, String language, Map<String, Object> inputData, SandboxLimits limits) {
        log.info("SandboxService 执行请求: language={}, scriptLength={}",
                language, script == null ? 0 : script.length());

        // 第 1 步：语言校验（确定性、不调 LLM、不起容器）。
        // 放在 Service 而非 Executor：语言支持是"应用层策略"，不应绑定到 Docker 实现。
        // 这样即使换一个非 Docker 的执行器（如进程内沙盒），语言策略仍然生效。
        if (language == null || !"python".equalsIgnoreCase(language)) {
            return format(SandboxResult.infrastructureError(
                    "不支持的语言: " + language + "（本期仅支持 python）"));
        }

        // 第 2 步：策略校验（确定性、不调 LLM、不起容器）
        String rejection = SandboxPolicy.validate(script);
        if (rejection != null) {
            log.warn("脚本被策略拒绝: {}", rejection);
            return format(SandboxResult.securityRejected(rejection));
        }

        // 第 3 步：执行（配额缺省用 DEFAULT）
        SandboxLimits effectiveLimits = (limits != null) ? limits : SandboxLimits.DEFAULT;
        SandboxResult result = executor.execute(effectiveLimits, script, language, inputData);

        // 第 4 步：格式化
        return format(result);
    }

    /**
     * 把 SandboxResult 格式化为人类 + 下游可读的文本。
     *
     * <p>格式包含：状态、退出码、耗时、是否截断、stdout、stderr、错误信息。
     * 下游 ReviewNode 据此判断 pass/fail，审计据此判断是否合规。
     */
    private String format(SandboxResult result) {
        StringBuilder sb = new StringBuilder();
        if (result.ok()) {
            sb.append("[SANDBOX OK] exitCode=").append(result.exitCode());
        } else if (result.timedOut()) {
            sb.append("[SANDBOX TIMEOUT]");
        } else if (result.securityRejected()) {
            sb.append("[SANDBOX REJECTED]");
        } else {
            sb.append("[SANDBOX ERROR]");
        }
        sb.append(" elapsed=").append(result.elapsedMs()).append("ms");
        if (result.truncated()) {
            sb.append(" truncated=true");
        }
        if (result.error() != null) {
            sb.append("\nerror: ").append(result.error());
        }
        if (result.stderr() != null && !result.stderr().isBlank()) {
            sb.append("\nstderr: ").append(result.stderr());
        }
        if (result.stdout() != null && !result.stdout().isBlank()) {
            sb.append("\nstdout: ").append(result.stdout());
        }
        return sb.toString();
    }
}
