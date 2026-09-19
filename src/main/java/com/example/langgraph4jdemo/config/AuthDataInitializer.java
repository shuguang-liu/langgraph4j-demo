package com.example.langgraph4jdemo.config;

import com.example.langgraph4jdemo.service.AuthService;
import com.example.langgraph4jdemo.util.Sm3Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author liushug
 * @description auth 库初始化：建表 + 内置账号种子 + 旧格式 threadId 迁移
 * <p>
 * - 新账号：随机生成 threadId 存库（与用户名无推导关系）
 * - 存量账号：密码等字段不覆盖；thread_id 若是旧版"SM4(用户名)"派生格式（u_ + 32 位连续 hex），
 *   自动重新生成为随机值（一次性迁移，保证永久 threadId 不再可被推算）
 */
@Slf4j
@Component
public class AuthDataInitializer implements ApplicationRunner {

    /** 旧版派生格式：u_ + SM4(用户名) 的 32 位连续 hex（128 位分组输出，无横杠） */
    private static final String LEGACY_THREAD_ID_REGEX = "^u_[0-9a-f]{32}$";

    private final JdbcTemplate authJdbcTemplate;
    private final AuthService authService;
    private final AuthProperties authProperties;

    public AuthDataInitializer(@Qualifier("authJdbcTemplate") JdbcTemplate authJdbcTemplate,
                               AuthService authService,
                               AuthProperties authProperties) {
        this.authJdbcTemplate = authJdbcTemplate;
        this.authService = authService;
        this.authProperties = authProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        authJdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_user (
                    id            BIGSERIAL PRIMARY KEY,
                    username      VARCHAR(64)  NOT NULL UNIQUE,
                    password_hash VARCHAR(64)  NOT NULL,
                    thread_id     VARCHAR(128) NOT NULL UNIQUE,
                    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""");
        authJdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_anonymous_quota (
                    ip             VARCHAR(64) PRIMARY KEY,
                    call_count     INT       NOT NULL DEFAULT 0,
                    blocked        BOOLEAN   NOT NULL DEFAULT FALSE,
                    last_call_time TIMESTAMP
                )""");
        // 旧表结构迁移：补 blocked 永久限流标记列（已存在时跳过）
        authJdbcTemplate.execute(
                "ALTER TABLE t_anonymous_quota ADD COLUMN IF NOT EXISTS blocked BOOLEAN NOT NULL DEFAULT FALSE");

        List<String> migrated = new ArrayList<>();
        List<String> usernames = authProperties.getUsers().stream().map(user -> {
            authJdbcTemplate.update("""
                    INSERT INTO t_user(username, password_hash, thread_id) VALUES (?, ?, ?)
                    ON CONFLICT (username) DO NOTHING
                    """,
                    user.getUsername(),
                    Sm3Util.hashHex(user.getPassword()),
                    authService.newThreadId());
            // 旧版由用户名推导的 threadId 一次性迁移为随机值
            int updated = authJdbcTemplate.update("""
                    UPDATE t_user SET thread_id = ?
                    WHERE username = ? AND thread_id ~ ?
                    """,
                    authService.newThreadId(), user.getUsername(), LEGACY_THREAD_ID_REGEX);
            if (updated > 0) {
                migrated.add(user.getUsername());
            }
            return user.getUsername();
        }).toList();

        if (migrated.isEmpty()) {
            log.info("[auth] 鉴权库就绪（第二个数据源），内置账号: {}", usernames);
        } else {
            log.info("[auth] 鉴权库就绪（第二个数据源），内置账号: {}，旧派生格式 threadId 已重新生成: {}", usernames, migrated);
        }
    }

}
