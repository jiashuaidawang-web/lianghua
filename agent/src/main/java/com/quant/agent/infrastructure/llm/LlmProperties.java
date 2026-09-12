package com.quant.agent.infrastructure.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

// ============================================================================================
// 【Day 1 · 阅读入口】LlmProperties —— LLM 连接的"账号密码本"。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：最底层配置对象。LlmConfiguration 靠它读 apiKey / baseUrl / modelName。
//   建议阅读时机：Day 1 读 QuantLlmService 之前先看它一眼。
//   学完能回答：工程的 LLM 账号信息从哪来？怎么和 application.yml 绑定？
//
//   Java 类比：就是一个 POJO + @ConfigurationProperties，等价于 Spring 的"类型安全配置"。
//     比 @Value("quant.llm.api-key") 散落在各处好：集中、有 IDE 自动补全、能校验。
//
//   绑定关系（application.yml → 这个类）：
//     quant:
//       llm:
//         api-key: xxx        →  this.apiKey
//         base-url: https://… →  this.baseUrl
//         model-name: LongCat →  this.modelName
//
//   ⬇ 下一步：看 LlmConfiguration，它读 these 属性，构建出 ChatModel / StreamingChatModel 这两个 Bean。
// ============================================================================================

// prefix = "quant.llm"：告诉 Spring，把 yml 里 quant.llm.* 这组配置自动塞进这个对象的字段。
@ConfigurationProperties(prefix = "quant.llm")
public class LlmProperties {
    // 三个字段名（apiKey/baseUrl/modelName）会和 yml 里的驼峰/中划线自动匹配：
    //   api-key / api_key → apiKey；model-name / model_name → modelName
    private String apiKey;
    private String baseUrl;
    private String modelName;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
}
