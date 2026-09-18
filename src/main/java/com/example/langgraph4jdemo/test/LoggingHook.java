package com.example.langgraph4jdemo.test;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author liushug
 * @description 全局的日志打印
 */
public class LoggingHook implements NodeHook.WrapCall<HelloGraph.MyState> {

    private static final Logger log = LoggerFactory.getLogger(LoggingHook.class);

    @Override
    public CompletableFuture<Map<String, Object>> applyWrap(String node, HelloGraph.MyState state, RunnableConfig runnableConfig, AsyncNodeActionWithConfig<HelloGraph.MyState> asyncNodeActionWithConfig) {
        long start = System.currentTimeMillis();
        log.info("[node]开始执行 " + node + " start");

        return asyncNodeActionWithConfig.apply(state, runnableConfig).whenComplete((result, ex) -> {
            long cost = System.currentTimeMillis() - start;
            if (ex != null) {
                log.error("[node]执行 " + node + " 失败，耗时 " + cost + "ms", ex);
            } else {
                log.info("[node]执行 " + node + " 成功，耗时 " + cost + "ms");
            }

        });
    }
}
