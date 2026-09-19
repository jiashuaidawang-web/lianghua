package com.quant.agent.infrastructure.audit;

import com.quant.agent.domain.audit.Capability;
import com.quant.agent.domain.audit.Capability.CapabilitySource;
import com.quant.agent.domain.audit.Capability.CapabilityType;
import com.quant.agent.infrastructure.mcp.McpClient;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// ============================================================================================
// 【Day 10 · 阅读入口】CapabilityInventory —— "能力清单"扫描器，Day 10 的数据源。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 10 Diff 引擎的"能力数据源"。扫描本地 @Tool + 远端 MCP，
//   产出 List<Capability>，供 DiffAuditService 与 Task 列表对比。
//   学完能回答：
//     1. 为什么扫描 Spring ApplicationContext 而不是扫源码？
//     2. 本地和远端能力怎么合并？
//     3. MCP 扫描失败怎么办？
//
//   💡 为什么扫 ApplicationContext 而不是源码？
//     源码反射（扫 @Tool 注解）能看到"潜在能力"，但和运行时可能不一致
//     （Bean 没注册、@Conditional 没满足）。
//     扫 ApplicationContext = 扫"实际存活的 Bean" = 和运行时一致。
//     这是 Day 10 能"确定性审计"的基础。
//
//   💡 本地和远端怎么合并？
//     本地：从 ApplicationContext 拿所有 Bean，反射找 @Tool 方法 → Capability(LOCAL)
//     远端：McpClient.listTools() → Capability(MCP)
//     两者合并 = 完整能力清单。
//
//   💡 MCP 扫描失败怎么办？
//     MCP 是可选的（可能没配远端 Server）。
//     扫描失败 → 只返回本地能力，不阻断审计。
//     这是"优雅降级"：有 MCP 审计更全，没 MCP 也能工作。
//
//   ⬇ 下一步：看 DiffAuditService（怎么用这个清单做 Diff）。
// ============================================================================================

/**
 * 能力清单扫描器。
 *
 * <p>扫描本 JVM 的 @Tool 方法（通过 Spring ApplicationContext）+ 远端 MCP 工具，
 * 产出完整的 Capability 清单。
 */
public class CapabilityInventory {

    private static final Logger log = LoggerFactory.getLogger(CapabilityInventory.class);

    private final ApplicationContext applicationContext;

    /**
     * @param applicationContext Spring 容器（用于扫描所有 Bean 的 @Tool 方法）
     */
    public CapabilityInventory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 扫描所有能力。
     *
     * @return 能力清单（本地 + 远端）
     */
    public List<Capability> scanAll() {
        List<Capability> capabilities = new ArrayList<>();

        // 第 1 步：扫描本地 @Tool
        capabilities.addAll(scanLocalTools());

        // 第 2 步：扫描远端 MCP（可选，失败不影响）
        capabilities.addAll(scanMcpTools());

        log.info("CapabilityInventory 扫描完成: 共 {} 个能力（本地 {} + 远端 {}）",
                capabilities.size(),
                scanLocalTools().size(),
                capabilities.size() - scanLocalTools().size());

        return capabilities;
    }

    /**
     * 扫描本地所有 Bean 的 @Tool 方法。
     */
    public List<Capability> scanLocalTools() {
        List<Capability> capabilities = new ArrayList<>();

        // 获取所有 Bean 名
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                capabilities.addAll(scanBeanTools(bean));
            } catch (Exception e) {
                log.debug("扫描 Bean 失败，跳过: beanName={}, error={}", beanName, e.getMessage());
            }
        }

        return capabilities;
    }

    /**
     * 扫描单个 Bean 的所有 @Tool 方法。
     */
    private List<Capability> scanBeanTools(Object bean) {
        List<Capability> capabilities = new ArrayList<>();

        for (java.lang.reflect.Method method : bean.getClass().getDeclaredMethods()) {
            Tool toolAnno = method.getAnnotation(Tool.class);
            if (toolAnno == null) {
                continue;
            }

            String name = method.getName();
            String description = String.join(" ", toolAnno.value());
            CapabilityType type = inferType(name, description);

            // 从方法名和描述提取 tags（用于模糊匹配）
            List<String> tags = extractTags(name, description);

            capabilities.add(new Capability(
                    name,
                    type,
                    CapabilitySource.LOCAL,
                    description,
                    tags));
        }

        return capabilities;
    }

    /**
     * 扫描远端 MCP 工具（可选）。
     * <p>没有 McpClient Bean 或扫描失败 → 返回空列表，不阻断。
     */
    private List<Capability> scanMcpTools() {
        List<Capability> capabilities = new ArrayList<>();

        // 尝试获取 McpClient Bean（可能不存在）
        McpClient mcpClient;
        try {
            mcpClient = applicationContext.getBean(McpClient.class);
        } catch (Exception e) {
            log.debug("未配置 McpClient Bean，跳过远端 MCP 扫描");
            return capabilities;
        }

        try {
            var tools = mcpClient.listTools();
            for (var entry : tools.entrySet()) {
                var def = entry.getValue();
                CapabilityType type = inferType(def.name(), def.description());
                capabilities.add(new Capability(
                        def.name(),
                        type,
                        CapabilitySource.MCP,
                        def.description(),
                        extractTags(def.name(), def.description())));
            }
        } catch (Exception e) {
            log.warn("MCP 远端工具扫描失败（不影响本地审计）: {}", e.getMessage());
        }

        return capabilities;
    }

    /**
     * 根据工具名和描述推断能力类型。
     * <p>这是启发式推断，用于 Diff 引擎做类别匹配。
     */
    private CapabilityType inferType(String name, String description) {
        String lower = (name + " " + description).toLowerCase();
        if (lower.contains("分析") || lower.contains("analysis") || lower.contains("推理")) {
            return CapabilityType.ANALYSIS;
        }
        if (lower.contains("价格") || lower.contains("基本面") || lower.contains("行情")
                || lower.contains("price") || lower.contains("fundamental") || lower.contains("fetch")) {
            return CapabilityType.DATA;
        }
        if (lower.contains("执行") || lower.contains("execute") || lower.contains("交易") || lower.contains("下单")) {
            return CapabilityType.EXECUTE;
        }
        if (lower.contains("通知") || lower.contains("notify") || lower.contains("告警")) {
            return CapabilityType.NOTIFY;
        }
        if (lower.contains("筛选") || lower.contains("filter")) {
            return CapabilityType.FILTER;
        }
        if (lower.contains("报告") || lower.contains("report") || lower.contains("生成")) {
            return CapabilityType.REPORT;
        }
        return CapabilityType.UNKNOWN;
    }

    /**
     * 从工具名和描述提取标签（用于模糊匹配）。
     */
    private List<String> extractTags(String name, String description) {
        List<String> tags = new ArrayList<>();
        String lower = (name + " " + description).toLowerCase();

        if (lower.contains("价格") || lower.contains("price")) tags.add("price");
        if (lower.contains("基本面") || lower.contains("fundamental") || lower.contains("pe") || lower.contains("pb")) tags.add("fundamental");
        if (lower.contains("行情") || lower.contains("market")) tags.add("market-data");
        if (lower.contains("资金") || lower.contains("flow")) tags.add("flow");
        if (lower.contains("历史") || lower.contains("history")) tags.add("history");
        if (lower.contains("股票") || lower.contains("stock")) tags.add("stock");

        return tags;
    }
}
