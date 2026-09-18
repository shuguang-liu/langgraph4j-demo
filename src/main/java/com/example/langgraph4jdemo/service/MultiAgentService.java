package com.example.langgraph4jdemo.service;

import com.example.langgraph4jdemo.dto.AgentResponse;
import com.example.langgraph4jdemo.dto.UserQuestion;
import com.example.langgraph4jdemo.state.MultiAgentState;
import com.example.langgraph4jdemo.state.MyAgentState;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.ai.chat.client.ChatClient;
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
public class MultiAgentService {

    @Autowired
    private CompiledGraph<MultiAgentState> multiAgentGraph;

    public AgentResponse ask(UserQuestion userQuestion) {
        var config = RunnableConfig.builder().threadId(userQuestion.getThreadId()).build();
        MultiAgentState state = multiAgentGraph.invoke(Map.of("message", List.of(new UserMessage(userQuestion.getQuestion()))), config).orElseThrow();
        // 从后往前找最后一条 AssistantMessage
        var snapshot = multiAgentGraph.getState(config);
        if (snapshot != null && "tools".equals(snapshot.next())) {
            return AgentResponse.pending(userQuestion.getThreadId());
        }
        return AgentResponse.completed(userQuestion.getThreadId(), extractAnswer(snapshot.state()));
    }

    public AgentResponse approve(String threadId, boolean approved) throws Exception {
        var config = RunnableConfig.builder().threadId(threadId).build();

        if (!approved) {
            multiAgentGraph.updateState(config, Map.of("rejected", true));
        }

        MultiAgentState state = multiAgentGraph.invoke(GraphInput.resume(), config).orElseThrow();
        return AgentResponse.completed(threadId, extractAnswer(state));
    }

    private String extractAnswer(MultiAgentState state) {
        List<Message> messages = state.message();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AssistantMessage aiMsg) {
                return aiMsg.getText();
            }
        }
        return "无回答";
    }

}
