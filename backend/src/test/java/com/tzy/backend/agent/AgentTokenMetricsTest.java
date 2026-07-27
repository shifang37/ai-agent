package com.tzy.backend.agent;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.tzy.backend.monitor.AgentMetricsCollector;
import com.tzy.backend.tools.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 任务 Token 用量实测：直接构造 SuperAgent 跑真实任务（同步 run），
 * 从 AgentMetricsCollector 读取每次任务的 token 消耗、LLM 调用次数/耗时、工具调用时延，
 * 输出平均值（与 /monitor/metrics 接口同一数据源，绕开 PGVector 数据库依赖）。
 * <p>
 * 运行：mvn test -Dtest=AgentTokenMetricsTest
 *
 * @author shifang37
 */
public class AgentTokenMetricsTest {

    private static final String[] TASKS = {
            "请直接给出一份3天杭州旅游的简要行程建议（不要使用文件写入或终端命令工具），完成后调用 terminate 工具结束任务",
            "请搜索\"2026年世界杯举办地\"，用一句话总结搜索结果，然后调用 terminate 工具结束任务",
            "请抓取网页 https://www.codefather.cn 并用两句话概括这个网站是做什么的，然后调用 terminate 工具结束任务"
    };

    @Test
    public void measureAgentTokenUsage() {
        String yml = ResourceUtil.readUtf8Str("application-local.yml");
        String dashScopeKey = extract(yml, "api-key:\\s*(sk-\\w+)");
        String searchApiKey = extract(yml, "search-api:\\s*\\n\\s*api-key:\\s*(\\S+)");

        DashScopeApi dashScopeApi = new DashScopeApi(dashScopeKey);
        ChatModel chatModel = new DashScopeChatModel(dashScopeApi,
                DashScopeChatOptions.builder().withModel("qwen-plus").build());
        ToolCallback[] allTools = ToolCallbacks.from(
                new FileOperationTool(),
                new WebSearchTool(searchApiKey),
                new WebScrapingTool(),
                new ResourceDownloadTool(),
                new TerminalOperationTool(),
                new PDFGenerationTool(),
                new TerminateTool()
        );

        List<Map<String, Object>> results = new ArrayList<>();
        for (String task : TASKS) {
            SuperAgent agent = new SuperAgent(allTools, chatModel);
            long start = System.currentTimeMillis();
            String answer = agent.run(task);
            long cost = System.currentTimeMillis() - start;
            Map<String, Object> snapshot = AgentMetricsCollector.getInstance().snapshot(agent.getAgentId());
            results.add(snapshot);
            System.out.println("\n===== 任务: " + task);
            System.out.println("任务总耗时: " + cost + " ms");
            System.out.println("指标: " + JSONUtil.toJsonStr(snapshot));
            System.out.println("结果摘要: " + answer.substring(0, Math.min(200, answer.length())));
        }

        // 汇总平均
        long avgTotal = avg(results, "totalTokens");
        long avgPrompt = avg(results, "promptTokens");
        long avgCompletion = avg(results, "completionTokens");
        long avgLlmCalls = avg(results, "llmCallCount");
        long avgLlmTime = avg(results, "llmAvgTimeMs");
        long avgToolTime = avg(results, "toolAvgTimeMs");
        System.out.println("\n========== 汇总（" + results.size() + " 个 Agent 任务平均） ==========");
        System.out.printf("平均消耗 token: %d（输入 %d / 输出 %d），平均 LLM 调用 %d 次，LLM 单次平均耗时 %d ms，工具调用平均耗时 %d ms%n",
                avgTotal, avgPrompt, avgCompletion, avgLlmCalls, avgLlmTime, avgToolTime);
    }

    private long avg(List<Map<String, Object>> results, String key) {
        return Math.round(results.stream()
                .mapToLong(m -> ((Number) m.getOrDefault(key, 0)).longValue())
                .average().orElse(0));
    }

    private String extract(String yml, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(yml);
        if (!matcher.find()) {
            throw new IllegalStateException("application-local.yml 中未找到: " + regex);
        }
        return matcher.group(1);
    }
}
