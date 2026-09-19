package com.example.langgraph4jdemo.exception;

/**
 * @author liushug
 * @description 账号已存在异常（创建账号时用户名冲突）
 */
public class AccountExistsException extends RuntimeException {

    public AccountExistsException(String username) {
        super("账号已存在: " + username);
    }

}
