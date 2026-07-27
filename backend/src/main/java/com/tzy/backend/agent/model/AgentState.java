package com.tzy.backend.agent.model;

/**
 * 代理执行状态的枚举类  
 *
 * @author shifang37
 */  
public enum AgentState {  
  
    /**  
     * 空闲状态  
     */  
    IDLE,  
  
    /**
     * 运行中状态
     */
    RUNNING,

    /**
     * 等待用户确认高危工具调用
     */
    WAITING_CONFIRM,

  
    /**  
     * 已完成状态  
     */  
    FINISHED,  
  
    /**  
     * 错误状态  
     */  
    ERROR  
}
