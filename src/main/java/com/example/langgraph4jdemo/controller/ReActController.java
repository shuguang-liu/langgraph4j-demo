package com.example.langgraph4jdemo.controller;

import com.example.langgraph4jdemo.dto.AgentResponse;
import com.example.langgraph4jdemo.dto.UserQuestion;
import com.example.langgraph4jdemo.service.ReActService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author liushug
 * @description
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/reAct")
public class ReActController {

    private final ReActService reActService;


    @RequestMapping(value = "/reActSearch")
    public AgentResponse ragAct(@RequestBody UserQuestion userQuestion){
        return reActService.ragAct(userQuestion);
    }

    @RequestMapping(value = "/approve")
    public AgentResponse approve(@RequestBody UserQuestion request) throws Exception {
        return reActService.approve(request.getThreadId(), request.isApproved());
    }

}
