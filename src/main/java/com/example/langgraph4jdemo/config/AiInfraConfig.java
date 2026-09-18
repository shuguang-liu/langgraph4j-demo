package com.example.langgraph4jdemo.config;

import com.example.langgraph4jdemo.tools.CustomerServiceTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * @author liushug
 * @description 公共配置类Config
 */
@Configuration
public class AiInfraConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder){
        return builder.build();
    }

    @Bean
    public ToolCallbackProvider toolCallbackProvider(CustomerServiceTools tools){
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    @Bean
    public RestTemplate restTemplate(){
        return new RestTemplate();
    }

}
