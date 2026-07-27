package com.tzy.backend.monitor;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单个会话（或单次 Agent 任务）的用量统计
 * 累计 LLM 输入/输出 token、调用次数与耗时，以及各工具的调用次数与时延
 *
 * @author shifang37
 */
@Getter
public class SessionMetrics {

    private final String sessionId;
    private final long createTime = System.currentTimeMillis();

    // LLM 调用统计
    private final AtomicLong promptTokens = new AtomicLong();
    private final AtomicLong completionTokens = new AtomicLong();
    private final AtomicLong totalTokens = new AtomicLong();
    private final AtomicLong llmCallCount = new AtomicLong();
    private final AtomicLong llmTotalTimeMs = new AtomicLong();

    // 工具调用统计
    private final AtomicLong toolCallCount = new AtomicLong();
    private final AtomicLong toolTotalTimeMs = new AtomicLong();
    private final Map<String, ToolStat> toolStats = new ConcurrentHashMap<>();

    @Getter
    public static class ToolStat {
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong totalTimeMs = new AtomicLong();
    }

    public SessionMetrics(String sessionId) {
        this.sessionId = sessionId;
    }

    public void recordLlmCall(Integer prompt, Integer completion, Integer total, long timeMs) {
        if (prompt != null) {
            promptTokens.addAndGet(prompt);
        }
        if (completion != null) {
            completionTokens.addAndGet(completion);
        }
        if (total != null) {
            totalTokens.addAndGet(total);
        }
        llmCallCount.incrementAndGet();
        llmTotalTimeMs.addAndGet(timeMs);
    }

    public void recordToolCall(String toolName, long timeMs) {
        toolCallCount.incrementAndGet();
        toolTotalTimeMs.addAndGet(timeMs);
        ToolStat stat = toolStats.computeIfAbsent(toolName, k -> new ToolStat());
        stat.count.incrementAndGet();
        stat.totalTimeMs.addAndGet(timeMs);
    }

    /**
     * 输出快照（含平均值），便于接口直接返回 JSON
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sessionId", sessionId);
        map.put("promptTokens", promptTokens.get());
        map.put("completionTokens", completionTokens.get());
        map.put("totalTokens", totalTokens.get());
        map.put("llmCallCount", llmCallCount.get());
        map.put("llmTotalTimeMs", llmTotalTimeMs.get());
        long llmCalls = llmCallCount.get();
        map.put("llmAvgTimeMs", llmCalls == 0 ? 0 : llmTotalTimeMs.get() / llmCalls);
        map.put("toolCallCount", toolCallCount.get());
        map.put("toolTotalTimeMs", toolTotalTimeMs.get());
        long toolCalls = toolCallCount.get();
        map.put("toolAvgTimeMs", toolCalls == 0 ? 0 : toolTotalTimeMs.get() / toolCalls);
        Map<String, Object> tools = new LinkedHashMap<>();
        toolStats.forEach((name, stat) -> {
            Map<String, Object> t = new LinkedHashMap<>();
            long count = stat.getCount().get();
            t.put("count", count);
            t.put("totalTimeMs", stat.getTotalTimeMs().get());
            t.put("avgTimeMs", count == 0 ? 0 : stat.getTotalTimeMs().get() / count);
            tools.put(name, t);
        });
        map.put("toolStats", tools);
        return map;
    }
}
