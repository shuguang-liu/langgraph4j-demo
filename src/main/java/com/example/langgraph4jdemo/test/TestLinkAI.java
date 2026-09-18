package com.example.langgraph4jdemo.test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author liushug
 * @description 测试调用 通义千问
 */
@RestController
@RequestMapping("/api/test")
public class TestLinkAI {

    private final ChatClient chatClient;

    public TestLinkAI(ChatModel chatModel){
        this.chatClient = ChatClient.builder(chatModel)
                .build();
    }

    @RequestMapping("/ask")
    public String ask(@RequestParam String question){
        return chatClient.prompt().user(question).call().content();
    }

}
