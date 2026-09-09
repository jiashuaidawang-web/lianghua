package com.quant.agent.graph.topology;

import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.graph.nodes.AnalysisNode;
import com.quant.agent.graph.nodes.OutputNode;
import com.quant.agent.graph.nodes.ToolNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * QuantAgentStateGraph 单元测试。
 *
 * <p>验证：图编译成功、条件边路由正确。
 * Node 内部逻辑用 mock 隔离，只测图拓扑本身。
 */
class QuantAgentStateGraphTest {

    /**
     * 构造一个图，AnalysisNode 的行为由 mock 控制。
     */
    private CompiledGraph<QuantAgentState> compileGraph(boolean needsTool) throws Exception {
        AnalysisNode analysisNode = mock(AnalysisNode.class);
        when(analysisNode.apply(any())).thenReturn(Map.of(
                "analysisResult", "BUY 8.5",
                "needsTool", needsTool));

        ToolNode toolNode = mock(ToolNode.class);
        when(toolNode.apply(any())).thenReturn(Map.of("toolData", "price=1500"));

        OutputNode outputNode = new OutputNode();

        QuantAgentStateGraph graph = new QuantAgentStateGraph(analysisNode, toolNode, outputNode);
        return graph.compile();
    }

    @Test
    void shouldCompileSuccessfully() throws Exception {
        // 图结构合法 → compile 不抛异常
        assertDoesNotThrow(() -> compileGraph(false));
    }

    @Test
    void shouldRouteToToolNodeWhenNeedsToolTrue() throws Exception {
        // needsTool=true → 走 analysis → tool → output
        CompiledGraph<QuantAgentState> compiled = compileGraph(true);

        Optional<QuantAgentState> output = compiled.invoke(Map.of("symbol", "600519"));

        assertTrue(output.isPresent());
        QuantAgentState state = output.get();
        assertNotNull(state.finalResult());
        assertTrue(state.finalResult().contains("toolData")
                || state.finalResult().contains("数据"), "应经过 toolNode，包含工具数据");
    }

    @Test
    void shouldRouteDirectlyToOutputWhenNeedsToolFalse() throws Exception {
        // needsTool=false → 走 analysis → output（跳过 toolNode）
        CompiledGraph<QuantAgentState> compiled = compileGraph(false);

        Optional<QuantAgentState> output = compiled.invoke(Map.of("symbol", "600519"));

        assertTrue(output.isPresent());
        QuantAgentState state = output.get();
        assertNull(state.toolData(), "needsTool=false 时不应经过 toolNode");
        assertNotNull(state.finalResult());
    }
}
