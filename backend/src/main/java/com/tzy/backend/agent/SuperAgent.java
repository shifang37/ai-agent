package com.tzy.backend.agent;

import com.tzy.backend.advisor.MyLoggerAdvisor;
import com.tzy.backend.advisor.TokenUsageMonitorAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * @author shifang37
 */
@Component
public class SuperAgent extends ToolCallAgent {

    public SuperAgent(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("superAgent");
        String SYSTEM_PROMPT = """  
                You are SuperAgent, an all-capable AI assistant, aimed at solving any task presented by the user.  
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.  
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """  
                Based on user needs, proactively select the most appropriate tool or combination of tools.  
                For complex tasks, you can break down the problem and use different tools step by step to solve it.  
                After using each tool, clearly explain the execution results and suggest the next steps.  
                If you want to stop the interaction at any point, use the `terminate` tool/function call.  
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(
                        new MyLoggerAdvisor(),
                        // 按本次 Agent 任务（agentId）累计 Token 用量与 LLM 调用耗时
                        new TokenUsageMonitorAdvisor(this.getAgentId())
                )
                .build();
        this.setChatClient(chatClient);
    }
}
