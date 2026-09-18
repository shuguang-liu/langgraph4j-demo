package com.example.langgraph4jdemo.service;

import com.example.langgraph4jdemo.dto.AgentResponse;
import com.example.langgraph4jdemo.dto.UserQuestion;
import com.example.langgraph4jdemo.state.MyAgentState;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @author liushug
 * @description
 */
@Service
public class ReActService {

    @Autowired
    private CompiledGraph<MyAgentState> reactAgent;

    /**
     *
     *  第一次调用：发起任务，可能停在 tools 前等待审批
     * @param userQuestion
     * @return
     */
    public AgentResponse ragAct(UserQuestion userQuestion) {
        var config = RunnableConfig.builder().threadId(userQuestion.getThreadId()).build();
        reactAgent.invoke(Map.of("message", List.of(new UserMessage(userQuestion.getQuestion()))), config);
        var snapshot = reactAgent.getState(config);
        if(snapshot != null && "tools".equals(snapshot.next())){
            return AgentResponse.pending(userQuestion.getThreadId());
        }

        return AgentResponse.completed(userQuestion.getThreadId(), extractAnswer(snapshot.state()));
    }

    /**
     * 第二次调用：用户审批后恢复执行
     */
    public AgentResponse approve(String threadId, boolean approved) throws Exception {
        var config = RunnableConfig.builder().threadId(threadId).build();

        if (approved) {
            // 批准：从暂停点恢复，走 tools 节点
            MyAgentState state = reactAgent.invoke(GraphInput.resume(), config).orElseThrow();
            return AgentResponse.completed(threadId, extractAnswer(state));
        } else {
            // 拒绝：修改 State，让条件边走到 reject 节点
            reactAgent.updateState(config, Map.of("rejected", true));
            MyAgentState state = reactAgent.invoke(GraphInput.resume(), config).orElseThrow();
            return AgentResponse.rejected(threadId);
        }
    }



    private String extractAnswer(MyAgentState state) {
        List<Message> messages = state.message();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AssistantMessage aiMsg) {
                return aiMsg.getText();
            }
        }
        return "未生成回答";
    }
}
