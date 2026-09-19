package com.quant.agent.graph.nodes;

import com.quant.agent.application.audit.DiffAuditService;
import com.quant.agent.domain.audit.GapMatrix;
import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.task.Task;
import com.quant.agent.domain.task.TaskType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DiffAuditNode 单元测试。
 *
 * <p>验证：审计逻辑正确（有 CRITICAL 缺口 → auditPassed=false，无缺口 → auditPassed=true）。
 */
class DiffAuditNodeTest {

    /**
     * 无缺口 → auditPassed = true。
     */
    @Test
    void shouldPassAuditWhenNoCriticalGaps() {
        // Fixture：DiffService 返回空矩阵
        DiffAuditService service = mock(DiffAuditService.class);
        when(service.audit(any())).thenReturn(GapMatrix.EMPTY);

        DiffAuditNode node = new DiffAuditNode(service);

        QuantAgentState state = new QuantAgentState(Map.of(
                "symbol", "600519",
                "tasks", List.of(Task.of(TaskType.ANALYSIS, "600519"))
        ));

        Map<String, Object> updates = node.apply(state);

        // 验证：auditPassed = true
        assertEquals(true, updates.get("auditPassed"));
        // 验证：GapMatrix 为空
        GapMatrix matrix = (GapMatrix) updates.get("gapMatrix");
        assertNotNull(matrix);
        assertTrue(matrix.isEmpty());
    }

    /**
     * 有 CRITICAL 缺口 → auditPassed = false。
     */
    @Test
    void shouldFailAuditWhenCriticalGapsExist() {
        // Fixture：DiffService 返回含 CRITICAL 的矩阵
        DiffAuditService service = mock(DiffAuditService.class);
        GapMatrix matrixWithCritical = mock(GapMatrix.class);
        when(matrixWithCritical.hasCritical()).thenReturn(true);
        when(matrixWithCritical.isEmpty()).thenReturn(false);
        when(matrixWithCritical.size()).thenReturn(1);
        when(matrixWithCritical.criticalCount()).thenReturn(1L);
        when(matrixWithCritical.summary()).thenReturn("审计发现 1 个缺口：(1 个 CRITICAL)");
        when(service.audit(any())).thenReturn(matrixWithCritical);

        DiffAuditNode node = new DiffAuditNode(service);

        QuantAgentState state = new QuantAgentState(Map.of(
                "symbol", "600519",
                "tasks", List.of(Task.of(TaskType.EXECUTE, "600519"))
        ));

        Map<String, Object> updates = node.apply(state);

        // 验证：auditPassed = false
        assertEquals(false, updates.get("auditPassed"));
        // 验证：写入了错误摘要
        assertNotNull(updates.get("errorMessage"));
    }
}
