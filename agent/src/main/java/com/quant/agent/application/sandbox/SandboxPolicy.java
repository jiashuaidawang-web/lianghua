package com.quant.agent.application.sandbox;

import java.util.List;
import java.util.regex.Pattern;

// ============================================================================================
// 【Day 11 · 阅读入口】SandboxPolicy —— 脚本的"安检门"，Day 11 的第一道确定性防线。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 11 安全校验的核心。在脚本进容器之前，由 Java 确定性检查。
//   建议阅读时机：Day 11 读完 constitution.md 的安全规则后读它。
//   学完能回答：
//     1. 为什么不靠 LLM 自觉，非要 Java 做校验？
//     2. 为什么用"黑名单"而不是"白名单"？
//     3. 这层校验和 Docker 隔离是什么关系？
//
//   💡 为什么不靠 LLM？
//     constitution 规定："LLM output is untrusted input"、"Risk-sensitive actions require deterministic policy checks"。
//     LLM 是概率性的，让它"自觉不生成危险代码"不可靠。
//     这道校验是确定性的：命中就是命中，不靠 LLM 的"觉悟"。
//
//   💡 为什么黑名单而不是白名单？
//     白名单（只允许已知安全模式）最安全，但太严格 —— 会误杀合法的因子计算代码。
//     黑名单（禁止已知危险模式）是"纵深防御"的第一层：
//       - 第 1 层：Java 黑名单（本类）—— 拦明显恶意
//       - 第 2 层：Docker 隔离（network none / read-only / cap-drop）—— 拦漏网之鱼
//     两层叠加 = 安全。单靠任一层都不够。
//
//   💡 和 Docker 隔离的关系？
//     Java 黑名单是"便宜的前置过滤"：命中就直接拒绝，省得起容器。
//     Docker 隔离是"兜底防线"：黑名单没拦住的（比如新型攻击），靠容器隔离限制损害。
//     两者是"串行"关系：先过黑名单，再起容器。
//
//   ⬇ 下一步：看 SandboxService（怎么编排 Policy + Executor）。
// ============================================================================================

/**
 * 脚本安全策略：在容器启动前，用确定性规则检查脚本是否含危险模式。
 *
 * <p>本期只执行 Python，黑名单覆盖：
 * <ul>
 *   <li>进程/系统命令执行（os.system、subprocess、eval、exec、__import__）</li>
 *   <li>网络访问（socket、urllib、requests、http.client）</li>
 *   <li>危险文件/路径操作（/shutil、/etc、/root 写入）</li>
 * </ul>
 */
public final class SandboxPolicy {

    private SandboxPolicy() {}

    /**
     * 危险模式黑名单。每条 = 一个正则。
     *
     * <p>用 ignoreCase 避免大小写绕过（如 Eval、SOCKET）。
     * 词边界 \b 避免子串误匹配（如 callback( 不匹配 \bcall\b、my_socket 不匹配 \bsocket\b）。
     *
     * <p>注意：本表是"纵深防御"的第一层，宁可误杀（保守）也不漏放。
     * 漏放的由第二层（Docker 隔离：network none / read-only / cap-drop ALL）兜底。
     */
    private static final List<Pattern> DENY_PATTERNS = List.of(
            // 进程 / 命令执行。
            // 分组 1：以单词字符结尾的 token（system/popen/Popen/check_output），两侧 \b 安全。
            Pattern.compile("\\b(os\\.system|os\\.popen|subprocess\\.|Popen|check_output)\\b", Pattern.CASE_INSENSITIVE),
            // 分组 2：call( —— 尾随 \b 在 ( 之后会失败（( 后常跟 [/'/空格，均非单词字符），故只要求前导 \b。
            Pattern.compile("\\bcall\\(", Pattern.CASE_INSENSITIVE),
            // eval / exec / __import__ / compile
            Pattern.compile("\\beval\\s*\\(|exec\\s*\\(|__import__\\s*\\(|compile\\s*\\(", Pattern.CASE_INSENSITIVE),
            // 网络：整词匹配（覆盖 import socket / socket.socket / urllib / requests / http.client / httpx）。
            Pattern.compile("\\b(socket|urllib|requests|http\\.client|httpx)\\b", Pattern.CASE_INSENSITIVE),
            // 危险模块
            Pattern.compile("\\b(shutil\\.(rmtree|move)|ctypes|multiprocessing)\\b", Pattern.CASE_INSENSITIVE),
            // 敏感路径（Docker 容器内主要是 unix 路径）
            Pattern.compile("(/etc/|/root/|/proc/)", Pattern.CASE_INSENSITIVE)
    );

    // 长度上限：防止用超长脚本绕过或撑爆
    private static final int MAX_SCRIPT_LENGTH = 100_000;

    /**
     * 检查脚本是否合规。
     *
     * @param script 脚本内容（来自 LLM，不可信）
     * @return null = 合规；非 null = 拒绝原因（直接给审计/用户看）
     */
    public static String validate(String script) {
        if (script == null || script.isBlank()) {
            return "脚本为空";
        }
        if (script.length() > MAX_SCRIPT_LENGTH) {
            return "脚本过长（>" + MAX_SCRIPT_LENGTH + " 字符）";
        }
        for (Pattern pattern : DENY_PATTERNS) {
            if (pattern.matcher(script).find()) {
                return "命中危险模式: " + pattern.pattern();
            }
        }
        return null;
    }
}
