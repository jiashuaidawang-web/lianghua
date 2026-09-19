package com.quant.agent.infrastructure.sandbox;

import com.quant.agent.domain.sandbox.SandboxLimits;
import com.quant.agent.domain.sandbox.SandboxResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// ============================================================================================
// 【Day 11 · 阅读入口】DockerSandboxExecutor —— 用"docker run"实现隔离执行的真实现。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 11 基础设施层的"真实现"。把脚本丢进容器，施加资源/网络/文件系统限制。
//   建议阅读时机：读完 SandboxExecutor 接口 + SandboxLimits 后读它。
//   学完能回答：
//     1. 为什么用 ProcessBuilder 调 docker，而不是 Docker SDK？
//     2. 每个安全限制对应哪个 docker run flag？
//     3. 超时是怎么实现的？为什么不能只靠 docker 的 --stop-timeout？
//     4. 为什么要把脚本写到文件再挂载，而不是直接传 stdin？
//
//   💡 为什么用 ProcessBuilder 而不是 Docker SDK？
//     项目 constitution 禁止引入 beta/snapshot 依赖。Docker SDK（docker-java）是第三方依赖，
//     且版本兼容需要维护。ProcessBuilder 调 docker CLI：
//       - 零新依赖（Docker CLI 是运维标配，不在 Maven 里）
//       - 和项目"用 JDK 原生实现"的风格一致（参考 Day 8 的 RateLimiter/Cache）
//     代价：需要 Docker CLI 在 PATH 里，且测试需要 mock/assumeDocker。
//
//   💡 安全限制 → docker run flag 对照：
//     --network none          → 默认无网络（networkAllowed=false 时）
//     --read-only             → 根文件系统只读
//     --tmpfs /tmp:rw,...     → 唯一可写位置（tmpfs，重启消失）
//     --memory {m}m           → 内存上限
//     --cpus {n}              → CPU 上限
//     --pids-limit {n}        → 进程数上限（防 fork 炸弹）
//     --cap-drop ALL          → 丢弃所有 Linux 能力（最严格）
//     --security-opt=no-new-privileges → 禁止提权
//     -v {host}:{container}:ro → 脚本以只读方式挂载
//     --rm                    → 运行完自动清理容器
//
//   💡 超时怎么实现？
//     两层：
//       1. Java 层：process.waitFor(timeoutMs, MILLISECONDS) → 超时后 destroyForcibly()
//       2. 容器层：脚本通过 stdin 接收"软超时"提示（可选）
//     只靠 docker --stop-timeout 不够：它只控制 docker stop 等几秒，
//     不控制进程实际执行时间。Java 层 waitFor 是硬超时。
//
//   💡 为什么挂载文件而不是 stdin？
//     stdin 也能传脚本（docker run -i ... python -），但：
//       - 大脚本可能超过 stdin 缓冲
//       - 挂载只读目录更符合"代码不可变"语义（脚本在容器里是只读的，防自修改）
//     所以：把脚本写到临时目录 → 只读挂载进容器 → 容器内 python /sandbox/script.py
//
//   ⬇ 下一步：看 SandboxTaskHandler（怎么把这个 Executor 接入图）。
// ============================================================================================

/**
 * 基于 Docker CLI 的沙盒执行器。
 *
 * <p>通过 ProcessBuilder 调用 {@code docker run}，施加网络/文件系统/资源限制。
 * 零第三方依赖（{@code docker} CLI 由运行环境提供）。
 *
 * <p>安全边界（按 SandboxLimits）：无网络、只读根文件系统、tmpfs 可写区、
 * 内存/CPU/进程数上限、丢弃所有能力、禁止提权。
 */
public class DockerSandboxExecutor implements SandboxExecutor {

    private static final Logger log = LoggerFactory.getLogger(DockerSandboxExecutor.class);

    /** 沙盒容器内脚本挂载点（只读）。 */
    private static final String CONTAINER_SCRIPT_DIR = "/sandbox";
    private static final String CONTAINER_SCRIPT_PATH = CONTAINER_SCRIPT_DIR + "/script.py";
    private static final String CONTAINER_INPUT_PATH = CONTAINER_SCRIPT_DIR + "/input.json";

    private final String image;

    /**
     * @param image Docker 镜像名（如 "python:3.11-slim"）
     */
    public DockerSandboxExecutor(String image) {
        this.image = (image == null || image.isBlank()) ? "python:3.11-slim" : image;
    }

    @Override
    public SandboxResult execute(SandboxLimits limits, String script, String language,
                                  Map<String, Object> inputData) {
        // 语言校验已在 SandboxService 完成（应用层策略，不绑定 Docker 实现）。
        // 本期只支持 python。
        if (!"python".equalsIgnoreCase(language)) {
            return SandboxResult.infrastructureError("不支持的语言: " + language + "（本期仅支持 python）");
        }

        if (!isDockerAvailable()) {
            return SandboxResult.infrastructureError("Docker CLI 不可用（未安装或不在 PATH 中）");
        }

        // 把脚本 + 输入数据写到临时目录，再只读挂载进容器。
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("lianghua-sandbox-");
            Path scriptFile = workDir.resolve("script.py");
            Path inputFile = workDir.resolve("input.json");
            Files.writeString(scriptFile, script, StandardCharsets.UTF_8);
            Files.writeString(inputFile, toJson(inputData), StandardCharsets.UTF_8);

            return runContainer(limits, workDir);
        } catch (IOException e) {
            log.warn("沙盒准备工作目录失败: {}", e.getMessage(), e);
            return SandboxResult.infrastructureError("准备工作目录失败: " + e.getMessage());
        } finally {
            // 清理临时目录，避免磁盘泄漏
            if (workDir != null) {
                deleteQuietly(workDir);
            }
        }
    }

    // ====================================================================
    // 容器运行
    // ====================================================================

    private SandboxResult runContainer(SandboxLimits limits, Path workDir) throws IOException {
        List<String> command = buildDockerCommand(limits, workDir);
        log.info("沙盒启动: image={}, timeout={}ms, memory={}m, network={}",
                image, limits.timeoutMs(), limits.memoryMb(), limits.networkAllowed());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false); // 分开 stdout / stderr，便于审计

        Process process = pb.start();
        long start = System.currentTimeMillis();

        // 用 try-with-resources 关闭 stdin（python 读到 EOF 才开始执行）
        try (OutputStream os = process.getOutputStream()) {
            // 不写内容：脚本已通过文件挂载，python 从文件读。
            // 关闭 stdin 让 python 知道没有更多输入。
        } catch (IOException ignored) {
        }

        boolean finished;
        try {
            finished = process.waitFor(limits.timeoutMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            destroyQuietly(process);
            return SandboxResult.infrastructureError("等待容器时被中断");
        }

        long elapsed = System.currentTimeMillis() - start;

        if (!finished) {
            // 硬超时：强制销毁容器
            destroyQuietly(process);
            log.warn("沙盒超时强制终止: elapsed={}ms, limit={}ms", elapsed, limits.timeoutMs());
            // 超时前可能已有部分输出
            String stdout = readLimited(process.getInputStream(), limits.maxOutputBytes());
            String stderr = readLimited(process.getErrorStream(), limits.maxOutputBytes());
            return SandboxResult.timedOut(stdout, stderr, elapsed, isTruncated(stdout, stderr, limits.maxOutputBytes()));
        }

        int exitCode = process.exitValue();
        String stdout = readLimited(process.getInputStream(), limits.maxOutputBytes());
        String stderr = readLimited(process.getErrorStream(), limits.maxOutputBytes());
        boolean truncated = isTruncated(stdout, stderr, limits.maxOutputBytes());

        log.info("沙盒完成: exitCode={}, elapsed={}ms, stdout={}, stderr={}, truncated={}",
                exitCode, elapsed, stdout.length(), stderr.length(), truncated);

        return SandboxResult.success(exitCode, stdout, stderr, elapsed, truncated);
    }

    // ====================================================================
    // 构造 docker run 命令
    // ====================================================================

    private List<String> buildDockerCommand(SandboxLimits limits, Path workDir) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");                       // 运行完自动清理
        cmd.add("--interactive");              // 允许 stdin（虽然本期不用，保留扩展）

        // 网络策略
        if (!limits.networkAllowed()) {
            cmd.add("--network");
            cmd.add("none");
        }

        // 文件系统：根只读 + tmpfs 可写区
        cmd.add("--read-only");
        cmd.add("--tmpfs");
        cmd.add("/tmp:rw,noexec,nosuid,size=" + limits.tmpMb() + "m");

        // 资源限制
        cmd.add("--memory");
        cmd.add(limits.memoryMb() + "m");
        cmd.add("--memory-swap");
        cmd.add(limits.memoryMb() + "m");      // 禁用 swap（memory == swap）
        cmd.add("--cpus");
        cmd.add(String.valueOf(limits.cpu()));
        cmd.add("--pids-limit");
        cmd.add(String.valueOf(limits.pidsLimit()));

        // 安全：丢弃所有能力 + 禁止提权
        cmd.add("--cap-drop");
        cmd.add("ALL");
        cmd.add("--security-opt");
        cmd.add("no-new-privileges");

        // 只读挂载脚本目录
        cmd.add("-v");
        cmd.add(workDir.toAbsolutePath() + ":" + CONTAINER_SCRIPT_DIR + ":ro");

        cmd.add(image);

        // 容器内执行：python 脚本，输入数据从 /sandbox/input.json 读
        cmd.add("python");
        cmd.add("-u");                         // 无缓冲输出
        cmd.add(CONTAINER_SCRIPT_PATH);

        return cmd;
    }

    // ====================================================================
    // 工具方法
    // ====================================================================

    /** 检测 Docker CLI 是否可用（轻量：只检查 docker --version）。 */
    static boolean isDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "--version").start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 读取流，截断到 maxBytes 字节。 */
    private String readLimited(java.io.InputStream stream, int maxBytes) {
        StringBuilder sb = new StringBuilder();
        int[] read = {0};
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 预检查：加上这行（含换行）会不会超
                int wouldBe = read[0] + line.length() + 1;
                if (wouldBe > maxBytes) {
                    sb.append(line, 0, Math.max(0, maxBytes - read[0]));
                    read[0] = maxBytes;
                    break;
                }
                sb.append(line).append('\n');
                read[0] = wouldBe;
            }
        } catch (IOException e) {
            log.warn("读取沙盒输出失败: {}", e.getMessage());
        }
        return sb.toString();
    }

    private boolean isTruncated(String stdout, String stderr, int maxBytes) {
        return stdout.length() + stderr.length() > maxBytes;
    }

    private void destroyQuietly(Process process) {
        try {
            process.destroyForcibly();
        } catch (Exception ignored) {
        }
    }

    private void deleteQuietly(Path dir) {
        try {
            Files.walk(dir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    /** 极简 JSON 序列化（避免引入额外依赖；inputData 是简单类型时够用）。 */
    private String toJson(Map<String, Object> inputData) {
        if (inputData == null || inputData.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : inputData.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append('"').append(String.valueOf(value).replace("\"", "\\\"")).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }
}
