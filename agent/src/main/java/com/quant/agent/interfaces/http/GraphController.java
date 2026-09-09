package com.quant.agent.interfaces.http;

import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.graph.runtime.GraphRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图执行端点。
 *
 * <p>GET /api/v1/graph/analyze?symbol=贵州茅台
 * 驱动整张 StateGraph 执行，返回最终结果。
 */
@RestController
public class GraphController {

    private final GraphRunner graphRunner;

    public GraphController(GraphRunner graphRunner) {
        this.graphRunner = graphRunner;
    }

    @GetMapping("/api/v1/graph/analyze")
    public String analyze(@RequestParam String symbol) {
        QuantAgentState finalState = graphRunner.run(symbol);
        return finalState.finalResult();
    }
}
