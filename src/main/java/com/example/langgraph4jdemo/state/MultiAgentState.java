package com.example.langgraph4jdemo.state;


import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author liushug
 * @description 多状态流转
 */
public class MultiAgentState extends AgentState {

    // 定义 schema: message 字段用appender（追加）
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            "message", Channels.appender(ArrayList::new), // 消息累计
            "next", Channels.base(() -> ""),  // supervisor 决定下一步
            "current_expert", Channels.base(() -> ""), // 当前 执行哪一个Agent
            "rejected", Channels.base(() -> "") // HITL 拒绝标记
    );

    public MultiAgentState(Map<String, Object> initData) {
        super(initData);
    }

    public List<Message> message() {
        Object raw = data().get("message");
        if (raw instanceof List) {
            return (List<Message>) raw;
        }
        return new ArrayList<>();
    }

    public String next(){
        return data().getOrDefault("next","").toString();
    }

    public String currentExpert() {
        return (String) data().getOrDefault("current_expert", "");
    }

    public boolean rejected() {
        return (boolean) data().getOrDefault("rejected", false);
    }

}
