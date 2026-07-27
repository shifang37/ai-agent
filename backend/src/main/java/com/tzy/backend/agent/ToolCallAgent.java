package com.tzy.backend.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.tzy.backend.agent.confirm.HumanConfirmManager;
import com.tzy.backend.agent.model.AgentState;
import com.tzy.backend.monitor.AgentMetricsCollector;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;


/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 *
 * @author shifang37
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    /**
     * 高危工具名单：执行前需要用户人工确认（Human-in-the-loop）
     */
    private static final Set<String> HIGH_RISK_TOOLS = Set.of(
            "executeTerminalCommand", // 终端命令执行
            "writeFile" // 文件写入
    );

    /**
     * 等待用户确认的超时时间（秒），超时视为拒绝
     */
    private static final long CONFIRM_TIMEOUT_SECONDS = 120;

    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存了工具调用信息的响应
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用内置的工具调用机制，自己维护上下文
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions = DashScopeChatOptions.builder()
                .withProxyToolCalls(true)
                .build();
    }

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        if (getNextStepPrompt() != null && !getNextStepPrompt().isEmpty()) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, chatOptions);
        try {
            // 获取带工具选项的响应
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .tools(availableTools)
                    .call()
                    .chatResponse();
            // 记录响应，用于 Act
            this.toolCallChatResponse = chatResponse;
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 输出提示信息
            String result = assistantMessage.getText();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            log.info(getName() + "的思考: " + result);
            log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s",
                            toolCall.name(),
                            toolCall.arguments())
                    )
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);
            if (toolCallList.isEmpty()) {
                // 只有不调用工具时，才记录助手消息
                getMessageList().add(assistantMessage);
                return false;
            } else {
                // 需要调用工具时，无需记录助手消息，因为调用工具时会自动记录
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程遇到了问题: " + e.getMessage());
            getMessageList().add(
                    new AssistantMessage("处理时遇到错误: " + e.getMessage()));
            return false;
        }
    }

    /**
     * 执行工具调用并处理结果
     *
     * @return 执行结果
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具调用";
        }
        // 高危工具人工确认（Human-in-the-loop）
        List<AssistantMessage.ToolCall> toolCallList = toolCallChatResponse.getResult().getOutput().getToolCalls();
        List<AssistantMessage.ToolCall> highRiskCalls = toolCallList.stream()
                .filter(toolCall -> HIGH_RISK_TOOLS.contains(toolCall.name()))
                .collect(Collectors.toList());
        if (!highRiskCalls.isEmpty() && !requestHumanConfirm(highRiskCalls)) {
            return rejectToolCalls(toolCallList);
        }
        // 调用工具
        Prompt prompt = new Prompt(getMessageList(), chatOptions);
        long toolStart = System.currentTimeMillis();
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        long toolCost = System.currentTimeMillis() - toolStart;
        // 记录消息上下文，conversationHistory 已经包含了助手消息和工具调用返回的结果
        setMessageList(toolExecutionResult.conversationHistory());
        // 当前工具调用的结果
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        // 上报工具执行时延：ToolCallingManager 按批次执行，观测不到单个工具的耗时，
        // 将批次耗时均摊到各工具，保证按会话汇总的总耗时准确
        List<ToolResponseMessage.ToolResponse> toolResponses = toolResponseMessage.getResponses();
        long avgToolCost = toolResponses.isEmpty() ? 0 : toolCost / toolResponses.size();
        toolResponses.forEach(response ->
                AgentMetricsCollector.getInstance().recordToolCall(getAgentId(), response.name(), avgToolCost));
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 完成了它的任务！结果: " + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info(results);
        // 终止工具被调用后结束执行循环，避免智能体空转到 maxSteps
        boolean terminateCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> "doTerminate".equals(response.name()));
        if (terminateCalled) {
            setState(AgentState.FINISHED);
        }
        return results;
    }

    /**
     * 通过 SSE 通道向用户推送高危工具确认请求，阻塞等待用户批准/拒绝
     * 状态机切换到 WAITING_CONFIRM，用户通过 /ai/manus/confirm 接口回调后恢复 RUNNING
     *
     * @param highRiskCalls 待确认的高危工具调用
     * @return 用户是否批准（超时视为拒绝）
     */
    private boolean requestHumanConfirm(List<AssistantMessage.ToolCall> highRiskCalls) {
        if (getEmitter() == null) {
            // 非流式运行（无推送通道）无法进行人工确认，默认拒绝，防止同步接口绕过审批执行高危操作
            log.warn("检测到高危工具调用但当前为非流式运行，无法人工确认，默认拒绝: {}",
                    highRiskCalls.stream().map(AssistantMessage.ToolCall::name).collect(Collectors.joining(", ")));
            return false;
        }
        setState(AgentState.WAITING_CONFIRM);
        CompletableFuture<Boolean> future = HumanConfirmManager.register(getAgentId());
        try {
            // 推送确认事件（前端解析 [CONFIRM] 前缀后弹出确认框）
            JSONArray tools = new JSONArray();
            highRiskCalls.forEach(toolCall -> tools.add(new JSONObject()
                    .set("name", toolCall.name())
                    .set("arguments", toolCall.arguments())));
            JSONObject payload = new JSONObject()
                    .set("agentId", getAgentId())
                    .set("tools", tools);
            getEmitter().send("[CONFIRM]" + payload);
            log.info("已推送高危工具确认请求，等待用户确认（最长 {} 秒）", CONFIRM_TIMEOUT_SECONDS);
            return future.get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("等待用户确认超时，视为拒绝执行");
            return false;
        } catch (Exception e) {
            log.error("等待用户确认时发生异常，视为拒绝执行", e);
            return false;
        } finally {
            HumanConfirmManager.remove(getAgentId());
            setState(AgentState.RUNNING);
        }
    }

    /**
     * 用户拒绝后，向模型回填「用户拒绝执行」的工具结果，保证消息上下文完整、循环可继续
     */
    private String rejectToolCalls(List<AssistantMessage.ToolCall> toolCallList) {
        getMessageList().add(toolCallChatResponse.getResult().getOutput());
        List<ToolResponseMessage.ToolResponse> responses = toolCallList.stream()
                .map(toolCall -> new ToolResponseMessage.ToolResponse(
                        toolCall.id(), toolCall.name(),
                        "用户拒绝执行该高危操作，请不要重试该操作，改用其他方式或结束任务。"))
                .collect(Collectors.toList());
        getMessageList().add(new ToolResponseMessage(responses));
        String rejected = toolCallList.stream()
                .map(AssistantMessage.ToolCall::name)
                .collect(Collectors.joining(", "));
        log.info("用户拒绝了高危工具调用: {}", rejected);
        return "用户拒绝了高危工具调用: " + rejected;
    }


    @Override
    public void cleanup() {

    }
}
