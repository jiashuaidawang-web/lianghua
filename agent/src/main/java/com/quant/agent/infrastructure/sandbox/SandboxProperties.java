package com.quant.agent.infrastructure.sandbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

// ============================================================================================
// 【Day 11 · 阅读入口】SandboxProperties —— 沙盒的"配置契约"，从 application.yml 绑定。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 11 的配置入口。AiServicesConfiguration 用它构造 DockerSandboxExecutor。
//   学完能回答：沙盒的 Docker 镜像从哪来？怎么和 application.yml 绑定？
//
//   💡 绑定关系（application.yml → 这个类）：
//     quant:
//       sandbox:
//         image: python:3.11-slim       → this.image
//
//   💡 为什么单独建一个 Properties，而不是硬编码镜像名？
//     不同环境可能需要不同镜像（如内网私有仓库、不同 Python 版本）。
//     放 yml 里 = 不改代码就能换镜像，符合"配置外置"原则。
//
//   ⬇ 下一步：看 AiServicesConfiguration（怎么用这个 Properties 构造 Bean）。
// ============================================================================================

/**
 * 沙盒配置属性（prefix = "quant.sandbox"）。
 *
 * @param image Docker 镜像名（默认 python:3.11-slim）
 */
@ConfigurationProperties(prefix = "quant.sandbox")
public record SandboxProperties(String image) {
    /** 默认镜像。 */
    public static final String DEFAULT_IMAGE = "python:3.11-slim";

    /**
     * 规范化：image 为空/空白 → 用默认值。
     * <p>Spring 绑定后调用，保证下游拿到的 image 永远非空。
     */
    public String image() {
        return (image == null || image.isBlank()) ? DEFAULT_IMAGE : image;
    }
}
