package com.quant.agent.application.audit;

import com.quant.agent.domain.audit.GapMatrix;
import com.quant.agent.domain.task.Task;
import com.quant.agent.domain.task.TaskType;
import com.quant.agent.infrastructure.audit.CapabilityInventory;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DiffAuditService 单元测试。
 *
 * <p>验证：审计逻辑正确（有 CRITICAL 缺口 → hasCritical=true，无缺口 → hasCritical=false）。
 */
class DiffAuditServiceTest {

    /**
     * 构造一个 mock 的 CapabilityInventory，返回固定的能力清单。
     */
    private CapabilityInventory mockInventory(List<com.quant.agent.domain.audit.Capability> capabilities) {
        CapabilityInventory inventory = mock(CapabilityInventory.class);
        when(inventory.scanAll()).thenReturn(capabilities);
        return inventory;
    }

    /**
     * 有 ANALYSIS 能力 + ANALYSIS 任务 → 无缺口。
     */
    @Test
    void shouldPassWhenCapabilityMatchesTask() {
        // Fixture：有 ANALYSIS 能力
        CapabilityInventory inventory = mockInventory(List.of(
                new com.quant.agent.domain.audit.Capability(
                        "analyzeStock",
                        com.quant.agent.domain.audit.Capability.CapabilityType.ANALYSIS,
                        com.quant.agent.domain.audit.Capability.CapabilitySource.LOCAL,
                        "分析股票投资价值",
                        List.of("analysis"))
        ));

        DiffAuditService service = new DiffAuditService(inventory);

        // ANALYSIS 任务
        List<Task> tasks = List.of(Task.of(TaskType.ANALYSIS, "600519"));

        GapMatrix matrix = service.audit(tasks);

        // 验证：无 CRITICAL 缺口
        assertFalse(matrix.hasCritical());
    }

    /**
     * 没有 EXECUTE 能力 + EXECUTE 任务 → CRITICAL 缺口。
     */
    @Test
    void shouldFindCriticalGapWhenExecuteCapabilityMissing() {
        // Fixture：没有 EXECUTE 能力
        CapabilityInventory inventory = mockInventory(List.of(
                new com.quant.agent.domain.audit.Capability(
                        "getStockPrice",
                        com.quant.agent.domain.audit.Capability.CapabilityType.DATA,
                        com.quant.agent.domain.audit.Capability.CapabilitySource.LOCAL,
                        "获取股票价格",
                        List.of("price", "market-data"))
        ));

        DiffAuditService service = new DiffAuditService(inventory);

        // EXECUTE 任务（高风险，缺失 = CRITICAL）
        List<Task> tasks = List.of(Task.of(TaskType.EXECUTE, "600519"));

        GapMatrix matrix = service.audit(tasks);

        // 验证：有 CRITICAL 缺口
        assertTrue(matrix.hasCritical());
        assertEquals(1, matrix.criticalCount());
    }

    /**
     * 没有 DATA 能力 + DATA_FETCH 任务 → CRITICAL 缺口。
     */
    @Test
    void shouldFindCriticalGapWhenDataCapabilityMissing() {
        // Fixture：没有 DATA 能力
        CapabilityInventory inventory = mockInventory(List.of(
                new com.quant.agent.domain.audit.Capability(
                        "analyzeStock",
                        com.quant.agent.domain.audit.Capability.CapabilityType.ANALYSIS,
                        com.quant.agent.domain.audit.Capability.CapabilitySource.LOCAL,
                        "分析股票",
                        List.of("analysis"))
        ));

        DiffAuditService service = new DiffAuditService(inventory);

        // DATA_FETCH 任务
        List<Task> tasks = List.of(Task.of(TaskType.DATA_FETCH, "600519"));

        GapMatrix matrix = service.audit(tasks);

        // 验证：有 CRITICAL 缺口
        assertTrue(matrix.hasCritical());
    }
}
