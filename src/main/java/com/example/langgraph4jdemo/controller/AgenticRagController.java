package com.example.langgraph4jdemo.controller;

import com.example.langgraph4jdemo.dto.UserQuestion;
import com.example.langgraph4jdemo.service.AgenticRagService;
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
@RequestMapping("/api/agenticRag")
public class AgenticRagController {

    private final AgenticRagService ragService;

    @RequestMapping(value = "/ragSearch", method = RequestMethod.POST)
    public String ragSearch(@RequestBody UserQuestion userQuestion){
        return ragService.ragSearch(userQuestion);
    }

}
