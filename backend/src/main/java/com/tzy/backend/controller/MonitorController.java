package com.tzy.backend.controller;

import com.tzy.backend.monitor.AgentMetricsCollector;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Token 用量与调用链路监控接口
 *
 * @author shifang37
 */
@RestController
@RequestMapping("/monitor")
public class MonitorController {

    /**
     * 查询全部会话的用量统计
     */
    @GetMapping("/metrics")
    public List<Map<String, Object>> getAllMetrics() {
        return AgentMetricsCollector.getInstance().snapshotAll();
    }

    /**
     * 查询指定会话（chatId 或 agentId）的用量统计
     */
    @GetMapping("/metrics/{sessionId}")
    public Map<String, Object> getMetrics(@PathVariable String sessionId) {
        return AgentMetricsCollector.getInstance().snapshot(sessionId);
    }
}
