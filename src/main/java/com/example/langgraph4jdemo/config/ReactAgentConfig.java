package com.example.langgraph4jdemo.config;

import com.example.langgraph4jdemo.state.MyAgentState;
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
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

/**
 * @author liushug
 * @description ReAct 配置
 */
@Configuration
public class ReactAgentConfig {

    private static final Set<String> SENSITIVE_TOOLS = Set.of(
            "createTicket"   // 危险工具名单
    );

    /**
     * 智能客服助手
     *
     * @param builder
     * @param tools
     * @return
     */
    @Bean("reactAgentChatClient")
    public ChatClient reactAgentChatClient(ChatClient.Builder builder, CustomerServiceTools tools) {
        return builder.defaultSystem("""
                        你是一个智能客服助手。
                        1. 用户问订单状态时，必须调用 queryOrder 工具
                        2. 用户问物流时，必须调用 queryLogistics 工具
                        3. 用户要退款/投诉时，必须调用 createTicket 工具
                        """)
                .defaultToolCallbacks(ToolCallbacks.from(tools))
                .build();
    }

    @Bean
    public NodeAction<MyAgentState> agentNode(@Qualifier("reactAgentChatClient") ChatClient chatClient, CustomerServiceTools tools) {
        return state -> {
            List<Message> messages = state.message();
            // 关键：禁用 Spring AI 的自动工具执行
            var options = ToolCallingChatOptions.builder()
//                    .toolCallbacks(ToolCallbacks.from(tools))
                    .internalToolExecutionEnabled(false)   // ← 关闭自动执行
                    .build();
            ChatResponse response = chatClient.prompt()
                    .messages(messages)
                    .options(options)
                    .call()
                    .chatResponse();

            AssistantMessage aiMsg = response.getResult().getOutput();
            // ↓↓↓ 加这三行
            System.out.println("=== finishReason = " + response.getResult().getMetadata().getFinishReason());
            System.out.println("=== hasToolCalls = " + aiMsg.hasToolCalls());
            System.out.println("=== textContent = " + aiMsg.getText());
            List<Message> newMessages = new ArrayList<>(messages);
            newMessages.add(aiMsg);
            return Map.of("message", newMessages);
        };
    }

    @Bean
    public NodeAction<MyAgentState> toolsNode(ToolCallbackProvider provider) {
        return myAgentState -> {
            System.out.println("执行了 toolsNode");
            List<Message> messages = myAgentState.message();
            Message lastMsg = messages.get(messages.size() - 1);
            if (lastMsg instanceof AssistantMessage aiMsg && aiMsg.hasToolCalls()) {
                List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                for (AssistantMessage.ToolCall toolCall : aiMsg.getToolCalls()) {
                    System.out.println("[toolsNode] 工具名: " + toolCall.name());
                    System.out.println("[toolsNode] 参数: " + toolCall.arguments());
                    String result = Arrays.stream(provider.getToolCallbacks())
                            .filter(tc -> tc.getToolDefinition().name().equals(toolCall.name()))
                            .findFirst()
                            .map(tc -> {
                                String r = tc.call(toolCall.arguments());
                                System.out.println("[toolsNode] 执行结果: [" + r + "]");   // ← 关键
                                return r;

                            })
                            .orElse("工具不存在");
                    responses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), result));
                }
                List<Message> newMessages = new ArrayList<>(messages);
                newMessages.add(ToolResponseMessage.builder().responses(responses).build());
                System.out.println("[toolsNode] 追加 ToolResponseMessage，共 " + responses.size() + " 个响应");
                return Map.of("message", newMessages);
            }
            System.out.println("[toolsNode] ⚠️ 没有 tool_calls，直接返回");
            return Map.of();
        };
    }

    @Bean
    public NodeAction<MyAgentState> rejectNode() {
        return state -> {
            List<Message> messages = state.message();
            Message lastMsg = messages.get(messages.size() - 1);

            if (lastMsg instanceof AssistantMessage aiMsg && aiMsg.hasToolCalls()) {
                List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                for (AssistantMessage.ToolCall toolCall : aiMsg.getToolCalls()) {
                    responses.add(new ToolResponseMessage.ToolResponse(
                            toolCall.id(), toolCall.name(), "用户拒绝了此操作"));
                }
                List<Message> newMessages = new ArrayList<>(messages);
                newMessages.add(ToolResponseMessage.builder().responses(responses).build());
                return Map.of("message", newMessages);
            }
            return Map.of();
        };
    }

    @Bean
    public EdgeAction<MyAgentState> shouldContinue() {
        return state -> {
            List<Message> messages = state.message();
            System.out.println("[shouldContinue] messages.size = " + messages.size());

            if (messages.isEmpty()) {
                System.out.println("[shouldContinue] 返回 end（消息为空）");
                return "end";
            }

            Message lastMsg = messages.get(messages.size() - 1);
            System.out.println("[shouldContinue] lastMsg 类型 = " + lastMsg.getClass().getSimpleName());

            if (lastMsg instanceof AssistantMessage aiMsg) {
                System.out.println("[shouldContinue] hasToolCalls = " + aiMsg.hasToolCalls());
                System.out.println("[shouldContinue] toolCalls = " + aiMsg.getToolCalls());

                if (aiMsg.hasToolCalls()) {
                    boolean hasSensitive = aiMsg.getToolCalls().stream()
                            .anyMatch(tc -> SENSITIVE_TOOLS.contains(tc.name()));
                    String route = hasSensitive ? "tools" : "auto_tools";
                    System.out.println("[shouldContinue] 返回 " + route);
                    return route;
                }
            }

            System.out.println("[shouldContinue] 返回 end（无 tool_calls）");
            return "end";
        };
    }

    @Bean
    public CompiledGraph<MyAgentState> reactAgent(
            NodeAction<MyAgentState> agentNode,
            NodeAction<MyAgentState> toolsNode,
            NodeAction<MyAgentState> rejectNode,
            EdgeAction<MyAgentState> shouldContinue) throws Exception {

        var serializer = new SpringAIJacksonStateSerializer<MyAgentState>(MyAgentState::new);
        var saver = new MemorySaver();
        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .interruptBefore("tools")
                .build();

        return new StateGraph<>(MyAgentState.SCHEMA, serializer)
                .addNode("agent", AsyncNodeAction.node_async(agentNode))
                .addNode("auto_tools", AsyncNodeAction.node_async(toolsNode))
                .addNode("tools", AsyncNodeAction.node_async(toolsNode))
                .addNode("reject", AsyncNodeAction.node_async(rejectNode))
                .addEdge(START, "agent")
                .addConditionalEdges("agent", AsyncEdgeAction.edge_async(shouldContinue),
                        Map.of("tools", "tools",
                                "auto_tools", "auto_tools",
                                "reject", "reject",
                                "end", END))
                .addEdge("tools", "agent")
                .addEdge("auto_tools", "agent")
                .addEdge("reject", "agent")
                .compile(compileConfig);
    }

}
