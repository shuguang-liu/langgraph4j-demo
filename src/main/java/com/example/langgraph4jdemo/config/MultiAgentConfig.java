package com.example.langgraph4jdemo.config;

import com.example.langgraph4jdemo.state.MultiAgentState;
import com.example.langgraph4jdemo.tools.CustomerServiceTools;
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
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

/**
 * @author liushug
 * @description TODO
 */
@Configuration
public class MultiAgentConfig {

    private static final Set<String> SENSITIVE_TOOLS = Set.of("createTicket");

    @Bean
    public NodeAction<MultiAgentState> supervisorNode(@Qualifier("supervisorChatClient") ChatClient chatClient) {
        return multiAgentState -> {
            List<Message> messages = multiAgentState.message();

            // 1. 已完成判断：最后一条是无 tool_calls 的 AssistantMessage
            if (!messages.isEmpty()) {
                Message lastMsg = messages.get(messages.size() - 1);
                if (lastMsg instanceof AssistantMessage aiMsg && !aiMsg.hasToolCalls()) {
                    System.out.println("[supervisor] 任务已完成 → end");
                    return Map.of("next", "end");
                }
            }

            // 2. 取最新一条用户消息
            String latestQuestion = messages.stream()
                    .filter(m -> m instanceof UserMessage)
                    .reduce((a, b) -> b)
                    .map(Message::getText)
                    .orElse("");
            String prompt = """
                    你是任务分配主管。根据用户问题，决定交给哪个专家处理。
                    
                    可选专家：
                    - cs：客服专家，处理订单、物流、退款、工单
                    - tech：技术专家，回答技术问题（RAG/LLM/框架）
                    - data：数据专家，处理统计、报表
                    - end：任务已完成
                    
                    只输出一个词：cs / tech / data / end
                    
                    用户问题：%s
                    """.formatted(latestQuestion);
            String decision = chatClient.prompt().user(prompt).call().content().trim().toLowerCase();
            System.out.println("[supervisor] 分配给: " + decision);
            // 一轮专家回复都没有时，"end" 会直接产出空回答——兜底改派客服专家
            boolean hasAssistantReply = messages.stream().anyMatch(m -> m instanceof AssistantMessage);
            if ("end".equals(decision) && !hasAssistantReply) {
                System.out.println("[supervisor] 首轮无专家回复，end 改派 cs");
                decision = "cs";
            }
            // 关键：同时记录当前Agent，供 backToExpert 使用
            return Map.of("next", decision, "current_expert", decision);
        };
    }

    @Bean
    public NodeAction<MultiAgentState> csExpertNode(@Qualifier("csChatClient") ChatClient chatClient, CustomerServiceTools tools) {
        return buildExpertNode(chatClient, tools, "cs_expert");
    }

    @Bean
    public NodeAction<MultiAgentState> techExpertNode(
            RestTemplate restTemplate) {   // ← 不需要 ChatClient
        return state -> {
            System.out.println("[tech_expert] 转发到 RAG 服务...");
            List<Message> messages = state.message();
            String question = messages.stream()
                    .filter(m -> m instanceof UserMessage)
                    .reduce((a, b) -> b)
                    .map(Message::getText)
                    .orElse("");

            // 直接调 RAG 服务，拿完整回答
            String answer;
            try {
                String url = "http://127.0.0.1:19092/api/chat/ask?question="
                        + URLEncoder.encode(question, StandardCharsets.UTF_8);
                answer = restTemplate.getForObject(url, String.class);
                System.out.println("[tech_expert] RAG 返回: "
                        + (answer != null && answer.length() > 100 ? answer.substring(0, 100) + "..." : answer));
            } catch (Exception e) {
                System.err.println("[tech_expert] 调用 RAG 失败: " + e.getMessage());
                answer = "抱歉，知识库服务暂时不可用。";
            }

            List<Message> newMessages = new ArrayList<>(messages);
            newMessages.add(new AssistantMessage(answer));
            return Map.of("message", newMessages);
        };
    }

    @Bean
    public NodeAction<MultiAgentState> dataExpertNode(@Qualifier("dataChatClient") ChatClient chatClient, RestTemplate restTemplate){
        return multiAgentState -> {
            System.out.println("[data_expert] 查询统计数据...");
            List<Message> messages = multiAgentState.message();
            String question = messages.stream()
                    .filter(m -> m instanceof UserMessage)
                    .reduce((a, b) -> b)
                    .map(Message::getText)
                    .orElse("");

            // 调统计接口
            String stats;
            try {
                stats = restTemplate.getForObject(
                        "http://127.0.0.1:19092/api/stats/orders",
                        String.class);
            } catch (Exception e) {
                stats = "统计服务暂时不可用";
            }

            // LLM 总结
            String answer = chatClient.prompt()
                    .system("你是数据分析师，基于提供的统计数据生成简洁的报表总结。")
                    .user("数据：\n" + stats + "\n\n用户问题：" + question)
                    .call()
                    .content();

            List<Message> newMessages = new ArrayList<>(messages);
            newMessages.add(new AssistantMessage(answer));
            return Map.of("message", newMessages);
        };
    }

    @Bean
    public EdgeAction<MultiAgentState> supervisorRouter() {
        return multiAgentState -> multiAgentState.next();
    }

    @Bean
    public EdgeAction<MultiAgentState> expertRouter(){
        return multiAgentState -> {
            List<Message> messages = multiAgentState.message();
            Message lastMsg = messages.get(messages.size() - 1);
            if(lastMsg instanceof AssistantMessage aiMsg && aiMsg.hasToolCalls()){
                boolean hasSensitive = aiMsg.getToolCalls().stream().anyMatch(tc -> SENSITIVE_TOOLS.contains(tc.name()));
                String route = hasSensitive ? "tools" : "auto_tools";
                System.out.println("[expertRouter] 有 tool_calls → " + route);
                return route;
            }
            System.out.println("[expertRouter] 无 tool_calls → supervisor");
            return "supervisor";
        };
    }

    @Bean
    public CompiledGraph<MultiAgentState> multiAgentGraph(
            NodeAction<MultiAgentState> supervisorNode,
            NodeAction<MultiAgentState> csExpertNode,
            NodeAction<MultiAgentState> techExpertNode,
            NodeAction<MultiAgentState> dataExpertNode,
            @Qualifier("multiAgentToolsNode") NodeAction<MultiAgentState> toolsNode,
            @Qualifier("multiAgentAutoToolsNode") NodeAction<MultiAgentState> autoToolsNode,
            EdgeAction<MultiAgentState> expertRouter,
            EdgeAction<MultiAgentState> backToExpert) throws Exception {
        var serialize = new SpringAIJacksonStateSerializer<MultiAgentState>(MultiAgentState::new);
        var saver = new MemorySaver();
        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .interruptBefore("tools")
                .build();

        return new StateGraph<MultiAgentState>(MultiAgentState.SCHEMA, serialize)
                .addNode("supervisor", AsyncNodeAction.node_async(supervisorNode))
                .addNode("auto_tools", AsyncNodeAction.node_async(autoToolsNode))
                .addNode("cs", AsyncNodeAction.node_async(csExpertNode))
                .addNode("tech", AsyncNodeAction.node_async(techExpertNode))
                .addNode("data", AsyncNodeAction.node_async(dataExpertNode))
                .addNode("tools", AsyncNodeAction.node_async(toolsNode))
                .addEdge(START, "supervisor")
                .addConditionalEdges(
                        "supervisor", AsyncEdgeAction.edge_async(multiAgentState -> multiAgentState.next()),
                        Map.of(
                                "cs", "cs",
                                "tech", "tech",
                                "data", "data",
                                "auto_tools","auto_tools",
                                "end", END))
                // Agent 判断： 有tool_calls -> tools/auto_tools； 无->supervisor
                .addConditionalEdges("cs",
                        AsyncEdgeAction.edge_async(expertRouter),
                        Map.of("tools", "tools",
                                "auto_tools", "auto_tools",
                                "supervisor", "supervisor"))
                .addConditionalEdges("tech",
                        AsyncEdgeAction.edge_async(expertRouter),
                        Map.of("tools", "tools",
                                "auto_tools", "auto_tools",
                                "supervisor", "supervisor"))
                .addConditionalEdges("data",
                        AsyncEdgeAction.edge_async(expertRouter),
                        Map.of("tools", "tools",
                                "auto_tools", "auto_tools",
                                "supervisor", "supervisor"))
                // 工具执行后回到Agent
                .addConditionalEdges("tools",
                        AsyncEdgeAction.edge_async(backToExpert),
                        Map.of("cs", "cs",
                                "tech", "tech",
                                "data", "data",
                                "supervisor", "supervisor"))
                .addConditionalEdges("auto_tools",
                        AsyncEdgeAction.edge_async(backToExpert),
                        Map.of("cs", "cs",
                                "tech", "tech",
                                "data", "data",
                                "supervisor", "supervisor"))
                .compile(compileConfig);
    }

    private NodeAction<MultiAgentState> buildExpertNode(ChatClient chatClient, CustomerServiceTools tools, String expertName){
        return multiAgentState -> {
            System.out.println("[" + expertName + "] 处理中...");
            List<Message> messages = multiAgentState.message();

            var options = ToolCallingChatOptions.builder()
                    .toolCallbacks(ToolCallbacks.from(tools))
                    .internalToolExecutionEnabled(false)   // ← 不自动执行，交给 tools 节点
                    .build();

            ChatResponse response = chatClient.prompt()
                    .messages(messages)
                    .options(options)
                    .call()
                    .chatResponse();

            AssistantMessage aiMsg = response.getResult().getOutput();
            System.out.println("[" + expertName + "] hasToolCalls = " + aiMsg.hasToolCalls());

            List<Message> newMessages = new ArrayList<>(messages);
            newMessages.add(aiMsg);

            // 不设置 next，交给 expertRouter 判断
            return Map.of("message", newMessages);
        };
    }

    /**
     * 工具执行节点
     * @param provider
     * @param nodeName
     * @return
     */
    private NodeAction<MultiAgentState> buildToolsNode(ToolCallbackProvider provider, String nodeName){
        return multiAgentState -> {
            System.out.println("[" + nodeName + "] 执行工具");
            List<Message> messages = multiAgentState.message();
            Message lastMsg = messages.get(messages.size() - 1);
            if(lastMsg instanceof AssistantMessage aiMsg && aiMsg.hasToolCalls()){
                List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                for(AssistantMessage.ToolCall tc : aiMsg.getToolCalls()){
                    String result = Arrays.stream(provider.getToolCallbacks())
                            .filter(x -> x.getToolDefinition().name().equals(tc.name()))
                            .findFirst()
                            .map(x -> x.call(tc.arguments()))
                            .orElse("工具不存在");
                    System.out.println("[" + nodeName + "] " + tc.name() + " → " + result);
                    responses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), result));
                }
                List<Message> newMessages = new ArrayList<>(messages);
                newMessages.add(ToolResponseMessage.builder().responses(responses).build());
                return Map.of("message", newMessages);
            }
            return Map.of();
        };
    }
    @Bean("multiAgentToolsNode")
    public NodeAction<MultiAgentState> toolsNode(ToolCallbackProvider provider) {
        return buildToolsNode(provider, "tools");
    }

    @Bean("multiAgentAutoToolsNode")
    public NodeAction<MultiAgentState> autoToolsNode(ToolCallbackProvider provider) {
        return buildToolsNode(provider, "auto_tools");
    }

    /**
     * 工具执行之后，回到Agent
     * @return
     */
    @Bean
    public EdgeAction<MultiAgentState> backToExpert() {
        return state -> {
            String expert = state.currentExpert();
            System.out.println("[backToExpert] 回到: " + expert);
            return expert.isEmpty() ? "supervisor" : expert;
        };
    }

}
