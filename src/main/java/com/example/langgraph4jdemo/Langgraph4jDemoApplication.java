package com.example.langgraph4jdemo;

import com.example.langgraph4jdemo.state.MyAgentState;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;
import java.util.Map;
import java.util.Scanner;

@SpringBootApplication
public class Langgraph4jDemoApplication {

    public static void main(String[] args) {
        System.out.println("项目启动中");
        SpringApplication.run(Langgraph4jDemoApplication.class, args);
        System.out.println("项目启动完成...");

    }

}
