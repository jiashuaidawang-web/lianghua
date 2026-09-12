package com.quant.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// ============================================================================================
// 【Day 0 · 阅读入口】工程启动入口 —— 这份文件是"开机按钮"，最简单，1 分钟看完。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Spring Boot 应用的 main 方法，最先执行。
//   建议阅读时机：Day 0 建立工程时读一遍即可，后续不用回头再看。
//   学完能回答：这个工程是怎么跑起来的？
//
//   调用链位置：
//     OS → java -jar → SpringApplication.run(QuantAgentApplication) → 扫描@Component/@Bean
//     → 把所有零件装配好 → 启动 WebFlux 服务器（端口 8080）→ 等 HTTP 请求
//
//   ⚠ 关键决定：工程用的是 Spring Boot WebFlux（响应式），不是 Spring MVC。
//     所以 Controller 可以返回 Flux<T>（流式），但也意味着：
//     你不能用 spring-boot-starter-web（MVC），否则启动就报错冲突。
//     pom.xml 里专门写了注释"Do not add spring-boot-starter-web alongside this"。
// ============================================================================================

@SpringBootApplication
public class QuantAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuantAgentApplication.class, args);
    }
}
