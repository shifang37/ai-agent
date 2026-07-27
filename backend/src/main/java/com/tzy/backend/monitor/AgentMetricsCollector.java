package com.tzy.backend.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 全局指标收集器（单例）
 * Advisor / Agent 在任意位置都可通过 getInstance() 上报，MonitorController 负责对外暴露
 *
 * @author shifang37
 */
@Slf4j
public final class AgentMetricsCollector {

    private static final AgentMetricsCollector INSTANCE = new AgentMetricsCollector();

    private final Map<String, SessionMetrics> metricsMap = new ConcurrentHashMap<>();

    private AgentMetricsCollector() {
    }

    public static AgentMetricsCollector getInstance() {
        return INSTANCE;
    }

    public void recordLlmCall(String sessionId, Usage usage, long timeMs) {
        SessionMetrics metrics = metricsMap.computeIfAbsent(sessionId, SessionMetrics::new);
        Integer prompt = usage == null ? null : usage.getPromptTokens();
        Integer completion = usage == null ? null : usage.getCompletionTokens();
        Integer total = usage == null ? null : usage.getTotalTokens();
        // 部分模型实现（如 DashScope）不填 completion 字段，用 total - prompt 推导
        if ((completion == null || completion == 0) && total != null && prompt != null && total >= prompt) {
            completion = total - prompt;
        }
        metrics.recordLlmCall(prompt, completion, total, timeMs);
        log.info("[监控] 会话 {} LLM 调用: 输入 {} tokens, 输出 {} tokens, 总计 {} tokens, 耗时 {} ms; 会话累计 {} tokens",
                sessionId, prompt, completion, total, timeMs, metrics.getTotalTokens().get());
    }

    public void recordToolCall(String sessionId, String toolName, long timeMs) {
        SessionMetrics metrics = metricsMap.computeIfAbsent(sessionId, SessionMetrics::new);
        metrics.recordToolCall(toolName, timeMs);
        log.info("[监控] 会话 {} 工具调用: {} 耗时 {} ms", sessionId, toolName, timeMs);
    }

    public Map<String, Object> snapshot(String sessionId) {
        SessionMetrics metrics = metricsMap.get(sessionId);
        return metrics == null ? Map.of() : metrics.snapshot();
    }

    public List<Map<String, Object>> snapshotAll() {
        return metricsMap.values().stream()
                .map(SessionMetrics::snapshot)
                .collect(Collectors.toList());
    }
}
