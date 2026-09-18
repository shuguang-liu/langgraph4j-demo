package com.example.langgraph4jdemo.controller;

import com.example.langgraph4jdemo.dto.AgentResponse;
import com.example.langgraph4jdemo.dto.UserQuestion;
import com.example.langgraph4jdemo.service.MultiAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author liushug
 * @description
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/multi")
public class MultiAgentController {

    private final MultiAgentService multiAgentService;

    /**
     * 根据问题类型，自动调用不同的工具
     * @param userQuestion
     * @return
     */
    @RequestMapping(value = "/ask", method = RequestMethod.POST)
    public AgentResponse ask(@RequestBody UserQuestion userQuestion){
        return multiAgentService.ask(userQuestion);
    }

    @RequestMapping(value = "/approve")
    public AgentResponse approve(@RequestBody UserQuestion request) throws Exception {
        return multiAgentService.approve(request.getThreadId(), request.isApproved());
    }

}
