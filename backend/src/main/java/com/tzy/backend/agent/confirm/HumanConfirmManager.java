package com.tzy.backend.agent.confirm;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 高危工具人工确认管理器（Human-in-the-loop）
 * <p>
 * Agent 在 act 阶段遇到高危工具调用时注册一个待确认 Future 并阻塞等待；
 * 用户通过 REST 确认接口（/ai/manus/confirm）批准或拒绝后，Agent 恢复执行循环。
 *
 * @author shifang37
 */
@Slf4j
public final class HumanConfirmManager {

    private static final Map<String, CompletableFuture<Boolean>> PENDING = new ConcurrentHashMap<>();

    private HumanConfirmManager() {
    }

    /**
     * 注册一个待确认请求
     *
     * @param agentId 智能体实例 ID
     * @return 用于阻塞等待用户决定的 Future
     */
    public static CompletableFuture<Boolean> register(String agentId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        PENDING.put(agentId, future);
        return future;
    }

    /**
     * 用户提交确认结果
     *
     * @param agentId  智能体实例 ID
     * @param approved 是否批准执行
     * @return 是否存在对应的待确认请求
     */
    public static boolean confirm(String agentId, boolean approved) {
        CompletableFuture<Boolean> future = PENDING.remove(agentId);
        if (future == null) {
            log.warn("确认请求不存在或已超时: agentId={}", agentId);
            return false;
        }
        log.info("用户{}了高危工具调用: agentId={}", approved ? "批准" : "拒绝", agentId);
        future.complete(approved);
        return true;
    }

    /**
     * 移除待确认请求（超时或异常时清理）
     */
    public static void remove(String agentId) {
        PENDING.remove(agentId);
    }
}
