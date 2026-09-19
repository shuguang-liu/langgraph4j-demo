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
        return builder.defaultSystem("""
                你是电商客服专家，处理订单、物流、退款、工单等问题。
                用户问订单时，必须调用 queryOrder 工具。
                用户问物流时，必须调用 queryLogistics 工具。
                用户要退款/投诉时，必须调用 createTicket 工具。
                """).build();
    }

    @Bean("techChatClient")
    public ChatClient techChatClient(ChatClient.Builder builder){
        return builder.defaultSystem("你是技术专家，回答 AI/LLM/RAG 相关问题。").build();
    }

    @Bean("dataChatClient")
    public ChatClient dataChatClient(ChatClient.Builder builder){
        return builder.defaultSystem("你是数据分析师，基于提供的统计数据生成简洁的报表总结。").build();
    }

}
