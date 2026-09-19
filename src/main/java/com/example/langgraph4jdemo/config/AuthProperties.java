package com.example.langgraph4jdemo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author liushug
 * @description 登录鉴权配置（app.auth 前缀）
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** SM4 密钥，必须 16 字节 */
    private String sm4Key;

    /** token 有效期（分钟），0 表示永不过期 */
    private long tokenExpireMinutes = 30;

    /** 未登录用户按 IP 限次 */
    private int anonymousLimit = 5;

    /** 内置账号（不做注册功能） */
    private List<UserAccount> users = new ArrayList<>();

    @Data
    public static class UserAccount {
        private String username;
        private String password;
    }

}
