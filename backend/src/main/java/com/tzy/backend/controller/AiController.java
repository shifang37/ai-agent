package com.tzy.backend.controller;

import com.tzy.backend.agent.SuperAgent;
import com.tzy.backend.agent.confirm.HumanConfirmManager;
import com.tzy.backend.app.LoveApp;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * @author shifang37
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private LoveApp loveApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @GetMapping("/love_app/chat/sync")
    public String doChatWithLoveAppSync(String message, String chatId) {
        return loveApp.doChat(message, chatId);
    }

    @GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppSSE(String message, String chatId) {
        return loveApp.doChatByStream(message, chatId);
    }

    /**
     * 流式调用 Manus 超级智能体
     *
     * @param message
     * @return
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        SuperAgent superAgent = new SuperAgent(allTools, dashscopeChatModel);
        return superAgent.runStream(message);
    }

    /**
     * 用户确认/拒绝高危工具调用（Human-in-the-loop）
     * Agent 在 WAITING_CONFIRM 状态下阻塞等待，此接口回调后恢复执行循环
     *
     * @param agentId  智能体实例 ID（随 SSE 确认事件下发）
     * @param approved 是否批准执行
     */
    @PostMapping("/manus/confirm")
    public String confirmManusToolCall(String agentId, boolean approved) {
        boolean success = HumanConfirmManager.confirm(agentId, approved);
        return success ? "ok" : "没有待确认的工具调用（可能已超时）";
    }



}
