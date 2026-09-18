package com.example.langgraph4jdemo.service;

import com.example.langgraph4jdemo.tools.CustomerServiceTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * @author liushug
 * @description 智能客服
 */
@Service
public class CustomerServiceAgent {
    private final ChatClient chatClient;

    public CustomerServiceAgent(ChatModel chatModel, CustomerServiceTools tools) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("""
                你是专业的电商客服助手。
                规则：
                1. 用户问订单/物流时，主动调用工具查询，不要凭空回答
                2. 创建工单前，先跟用户确认订单号和问题描述
                3. 回答简洁友好，不超过 150 字
                """).defaultTools(tools) // - 注册工具
                .build();
    }

    public String chat(String message) {
        return chatClient.prompt().user(message).call().content();
    }
}
