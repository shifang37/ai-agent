package com.tzy.backend.advisor;

import com.tzy.backend.monitor.AgentMetricsCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.MessageAggregator;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

/**
 * Token 用量与调用链路监控 Advisor
 * 从 ChatResponse metadata 中提取 TokenUsage，按会话累计输入/输出 token 与请求耗时，
 * 上报到 {@link AgentMetricsCollector}，可通过 /monitor/metrics 接口查看统计
 *
 * @author shifang37
 */
@Slf4j
public class TokenUsageMonitorAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

    /**
     * 固定会话 ID（Agent 场景使用）；为空时从对话记忆上下文中解析 chatId
     */
    private final String fixedSessionId;

    public TokenUsageMonitorAdvisor() {
        this(null);
    }

    public TokenUsageMonitorAdvisor(String fixedSessionId) {
        this.fixedSessionId = fixedSessionId;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        // 尽量靠外层执行，统计到包含 RAG 检索等在内的完整链路耗时
        return -100;
    }

    private String resolveSessionId(AdvisedRequest request) {
        if (fixedSessionId != null) {
            return fixedSessionId;
        }
        Object chatId = request.adviseContext().get(CHAT_MEMORY_CONVERSATION_ID_KEY);
        return chatId != null ? chatId.toString() : "default";
    }

    private void record(AdvisedRequest request, AdvisedResponse advisedResponse, long timeMs) {
        try {
            Usage usage = advisedResponse.response() == null ? null
                    : advisedResponse.response().getMetadata().getUsage();
            AgentMetricsCollector.getInstance().recordLlmCall(resolveSessionId(request), usage, timeMs);
        } catch (Exception e) {
            log.warn("记录 Token 用量失败: {}", e.getMessage());
        }
    }

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        long start = System.currentTimeMillis();
        AdvisedResponse advisedResponse = chain.nextAroundCall(advisedRequest);
        record(advisedRequest, advisedResponse, System.currentTimeMillis() - start);
        return advisedResponse;
    }

    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        long start = System.currentTimeMillis();
        Flux<AdvisedResponse> advisedResponses = chain.nextAroundStream(advisedRequest);
        return new MessageAggregator().aggregateAdvisedResponse(advisedResponses,
                response -> record(advisedRequest, response, System.currentTimeMillis() - start));
    }
}
