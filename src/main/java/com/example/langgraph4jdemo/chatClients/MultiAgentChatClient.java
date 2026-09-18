package com.example.langgraph4jdemo.chatClients;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author liushug
 * @description TODO
 */
@Configuration
public class MultiAgentChatClient {

    @Bean("supervisorChatClient")
    public ChatClient supervisorChatClient(ChatClient.Builder builder){
        return builder.build();
    }

    @Bean("csChatClient")
    public ChatClient csChatClient(ChatClient.Builder builder){
        return builder.defaultSystem("你是专业的电商客服助手，处理订单、物流、退款问题。").build();
    }

    @Bean("techChatClient")
    public ChatClient techChatClient(ChatClient.Builder builder){
        return builder.defaultSystem("你是技术专家，回答 AI/LLM/RAG 相关问题。").build();
    }

    @Bean("dataChatClient")
    public ChatClient dataChatClient(ChatClient.Builder builder){
        return builder.defaultSystem("你是报表生成专家，回答 报表 相关问题。").build();
    }

}
