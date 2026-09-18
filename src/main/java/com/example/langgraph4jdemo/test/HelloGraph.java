package com.example.langgraph4jdemo.test;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.hook.EdgeHook;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * @author liushug
 * @description 先用来查看怎么做的
 */
public class HelloGraph {

    // 1. 定义 State (用 Map 也行，先用最简单的)
    static class MyState extends AgentState{
        public MyState(Map<String, Object> init){
            super(init);
        }
    }

    public static void main(String[] args) throws GraphStateException {

        // 2. 构建图
        StateGraph<MyState> graph = new StateGraph<>(MyState::new)
                // 节点1：打招呼
                .addNode("greet", node_async(state ->{
                    System.out.println("[greet] 执行");
                    return Map.of("message", "你好，我是 LangGraph4j");
                }))
                // 节点2：判断情绪
                .addNode("check", node_async(state ->{
                    String msg = state.value("message").orElse("").toString();
                    boolean happy = msg.contains("不开心");
                    System.out.println("[check] 情绪判断：" + (happy ? "积极" : "消极"));
                    return Map.of("mood", happy ? "happy":"sad");
                }))
                // 节点3：积极分支
                .addNode("celebrate", node_async(state ->{
                    System.out.println("[celebrate] 恭喜你，你很棒");
                    return Map.of();
                }))
                // 节点4：消极分支
                .addNode("encourage", node_async(state ->{
                    System.out.println("[encourage] 加油，你很棒");
                    return Map.of();
                }))
                // 节点5： 日志打印
                .addNode("log", node_async(state ->{
                    System.out.println("[log] " + state.data());
                    return Map.of();
                }))
                // 3. 连边
                .addEdge(START, "greet")
                .addEdge("greet", "check")
                // 条件边：根据 mood 决定走哪个分支
                .addConditionalEdges("check",
                        AsyncEdgeAction.edge_async(state -> (String)state.value("mood").orElse("sad")),
                        Map.of("happy", "celebrate", "sad", "encourage"))
                .addEdge("celebrate", END)
                .addEdge("encourage", END)
                .addWrapCallNodeHook(new LoggingHook());

        // 4. 编译并运行
        CompiledGraph<MyState> app = graph.compile();
        app.stream(Map.of()).forEach(myStateNodeOutput -> {
            System.out.println("节点 " + myStateNodeOutput.node());
            System.out.println("状态 " + myStateNodeOutput.state());
        });
        app.invoke(Map.of()).ifPresent(finalState -> System.out.println("[finalState] " + finalState.data()));

        GraphRepresentation diagram = graph.getGraph(GraphRepresentation.Type.MERMAID, "My Workflow");
        System.out.println(diagram.content());

    }


}
