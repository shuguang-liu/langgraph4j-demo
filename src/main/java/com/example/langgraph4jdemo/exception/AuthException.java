package com.example.langgraph4jdemo.exception;

/**
 * @author liushug
 * @description 登录鉴权异常（用户名密码错误等）
 */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }

}
