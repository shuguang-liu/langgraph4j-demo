package com.example.langgraph4jdemo.config;

import com.example.langgraph4jdemo.state.MyAgentState;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.spring.ai.serializer.jackson.SpringAIJacksonStateSerializer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

/**
 * @author liushug
 * @description Agentic RAG 配置
 */
@Configuration
public class AgenticRagConfig {

    @Bean("agenticRagChatClient")
    public ChatClient agenticRagChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public NodeAction<MyAgentState> routerNode(@Qualifier("agenticRagChatClient") ChatClient chatClient) {
        return state -> {
            List<Message> messages = state.message();
            String question = messages.get(messages.size() - 1).getText();

            String prompt = """
                    你是一个路由分类器。判断用户问题是否需要查询技术知识库。
                    只输出 RETRIEVE 或 DIRECT，不要输出任何其他文字。

                    用户问题：%s
                    """.formatted(question);

            String decision = chatClient.prompt().user(prompt).call().content().trim().toUpperCase();
            System.out.println("[router] 判断结果: " + decision);

            return Map.of("route", decision.contains("RETRIEVE") ? "retrieve" : "direct");
        };
    }

    @Bean
    public NodeAction<MyAgentState> retrieveNode(RestTemplate restTemplate) {
        return state -> {
            List<Message> messages = state.message();
            String question = messages.get(messages.size() - 1).getText();

            System.out.println("[retrieve] 调用 RAG 服务: " + question);
            String context = restTemplate.getForObject(
                    "http://127.0.0.1:19092/api/chat/ask?question=" +
                            URLEncoder.encode(question, StandardCharsets.UTF_8),
                    String.class);

            return Map.of("context", context == null ? "" : context);
        };
    }

    @Bean
    public NodeAction<MyAgentState> generateNode(@Qualifier("agenticRagChatClient") ChatClient chatClient) {
        return state -> {
            String context = (String) state.value("context").orElse("");
            List<Message> messages = state.message();
            String question = messages.get(messages.size() - 1).getText();

            String answer = chatClient.prompt()
                    .system("严格基于参考资料回答，不要编造。")
                    .user("参考资料：\n" + context + "\n\n问题：" + question)
                    .call()
                    .content();

            List<Message> newMessages = new ArrayList<>(messages);
            newMessages.add(new AssistantMessage(answer));
            return Map.of("message", newMessages);
        };
    }

    @Bean
    public NodeAction<MyAgentState> directNode(@Qualifier("agenticRagChatClient") ChatClient chatClient) {
        return state -> {
            List<Message> messages = state.message();
            String question = messages.get(messages.size() - 1).getText();

            String answer = chatClient.prompt().user(question).call().content();

            List<Message> newMessages = new ArrayList<>(messages);
            newMessages.add(new AssistantMessage(answer));
            return Map.of("message", newMessages);
        };
    }

    @Bean
    public EdgeAction<MyAgentState> routeAfterRouter() {
        return state -> (String) state.value("route").orElse("direct");
    }

    @Bean
    public CompiledGraph<MyAgentState> agenticRag(
            NodeAction<MyAgentState> routerNode,
            NodeAction<MyAgentState> retrieveNode,
            NodeAction<MyAgentState> generateNode,
            NodeAction<MyAgentState> directNode,
            EdgeAction<MyAgentState> routeAfterRouter) throws Exception {

        var serializer = new SpringAIJacksonStateSerializer<MyAgentState>(MyAgentState::new);
        var saver = new MemorySaver();
        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();
        return new StateGraph<>(MyAgentState.SCHEMA, serializer)
                .addNode("router", AsyncNodeAction.node_async(routerNode))
                .addNode("retrieve", AsyncNodeAction.node_async(retrieveNode))
                .addNode("generate", AsyncNodeAction.node_async(generateNode))
                .addNode("direct", AsyncNodeAction.node_async(directNode))
                .addEdge(START, "router")
                .addConditionalEdges("router", AsyncEdgeAction.edge_async(routeAfterRouter),
                        Map.of("retrieve", "retrieve", "direct", "direct"))
                .addEdge("retrieve", "generate")
                .addEdge("generate", END)
                .addEdge("direct", END)
                .compile(compileConfig);
    }



}
