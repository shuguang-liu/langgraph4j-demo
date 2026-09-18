package com.example.langgraph4jdemo.state;


import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author liushug
 * @description 状态
 */
public class MyAgentState extends AgentState {

    // 定义 schema: message 字段用appender（追加）
    public static final Map<String, Channel<?>> SCHEMA = Map.of("message", Channels.appender(ArrayList::new));

    public MyAgentState(Map<String, Object> initData) {
        super(initData);
    }

    public List<Message> message(){
        Object raw = data().get("message");
        if (raw instanceof List) {
            return (List<Message>) raw;
        }
        return new ArrayList<>();
    }

}
