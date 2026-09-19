package com.example.langgraph4jdemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author liushug
 * @description 登录响应：SM4 加密的 token + 随机生成的永久 threadId
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {

    /** SM4 加密的 token */
    private String token;

    /** 永久 threadId，调用业务接口必须携带此字段 */
    private String threadId;

    private String username;

    /** token 过期时间（毫秒时间戳），前端据此提前重新登录；0 表示永久有效 */
    private Long expireAt;

}
