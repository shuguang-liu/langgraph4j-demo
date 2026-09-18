package com.example.langgraph4jdemo.dto;

import lombok.Data;

/**
 * @author liushug
 * @description
 */
@Data
public class UserQuestion {

    private String question;

    private String threadId;

    private boolean approved;

}
