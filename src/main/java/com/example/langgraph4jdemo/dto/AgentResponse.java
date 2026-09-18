package com.example.langgraph4jdemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author liushug
 * @description
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentResponse {
    private String status;       // PENDING_APPROVAL | COMPLETED | REJECTED
    private String threadId;
    private String answer;       // COMPLETED 时有值
    private String message;      // PENDING_APPROVAL 时的提示

    public static AgentResponse pending(String threadId) {
        return new AgentResponse("PENDING_APPROVAL", threadId, null, "Agent 想调用工具，请确认");
    }
    public static AgentResponse completed(String threadId, String answer) {
        return new AgentResponse("COMPLETED", threadId, answer, null);
    }
    public static AgentResponse rejected(String threadId) {
        return new AgentResponse("REJECTED", threadId, null, "用户拒绝了本次操作");
    }

}
