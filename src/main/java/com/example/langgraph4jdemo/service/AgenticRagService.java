package com.example.langgraph4jdemo.service;

import com.example.langgraph4jdemo.dto.UserQuestion;
import com.example.langgraph4jdemo.state.MyAgentState;
import org.bsc.langgraph4j.CompiledGraph;
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
public class AgenticRagService {

    @Autowired
    private CompiledGraph<MyAgentState> agenticRag;

    public String ragSearch(UserQuestion userQuestion) {
        var config = RunnableConfig.builder().threadId(userQuestion.getThreadId()).build();
        MyAgentState state = agenticRag.invoke(Map.of("message", List.of(new UserMessage(userQuestion.getQuestion()))), config).orElseThrow();
        // 从后往前找最后一条 AssistantMessage
        List<Message> messages = state.message();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AssistantMessage aiMsg) {
                return aiMsg.getText();
            }
        }
        return "未生成回答";
    }
}
