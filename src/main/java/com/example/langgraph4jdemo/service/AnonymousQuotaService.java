package com.example.langgraph4jdemo.service;

import com.example.langgraph4jdemo.config.AuthProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author liushug
 * @description 未登录用户按 IP 限流（计数持久化在 auth 库 t_anonymous_quota）。
 * IP 一旦超出限额，立即打上 blocked 永久限流标记并持久化：
 * 之后无论重启应用、还是计数被手动清零，该 IP 都会被直接拒绝（解封只能手动改库）。
 */
@Service
public class AnonymousQuotaService {

    private final AuthProperties authProperties;
    private final JdbcTemplate authJdbcTemplate;

    public AnonymousQuotaService(AuthProperties authProperties,
                                 @Qualifier("authJdbcTemplate") JdbcTemplate authJdbcTemplate) {
        this.authProperties = authProperties;
        this.authJdbcTemplate = authJdbcTemplate;
    }

    /**
     * 记一次未登录调用
     *
     * @return true = 放行（本次仍在限额内），false = 已超限或已被永久限流
     */
    public boolean tryAcquire(String ip) {
        // 原子 upsert：新 IP 计 1 次，老 IP 计数 +1，并带回是否已被永久限流
        Map<String, Object> row = authJdbcTemplate.queryForMap("""
                INSERT INTO t_anonymous_quota(ip, call_count, last_call_time, blocked)
                VALUES (?, 1, CURRENT_TIMESTAMP, FALSE)
                ON CONFLICT (ip) DO UPDATE
                   SET call_count = t_anonymous_quota.call_count + 1,
                       last_call_time = CURRENT_TIMESTAMP
                RETURNING call_count, blocked
                """, ip);
        int callCount = ((Number) row.get("call_count")).intValue();
        if (row.get("blocked") != null && (Boolean) row.get("blocked")) {
            return false;
        }
        if (callCount > authProperties.getAnonymousLimit()) {
            // 超限瞬间打上永久标记并持久化，之后仅凭 blocked 标记拒绝
            authJdbcTemplate.update("UPDATE t_anonymous_quota SET blocked = TRUE WHERE ip = ?", ip);
            return false;
        }
        return true;
    }

    public boolean isBlocked(String ip) {
        Boolean blocked = authJdbcTemplate.queryForObject(
                "SELECT blocked FROM t_anonymous_quota WHERE ip = ?", Boolean.class, ip);
        return Boolean.TRUE.equals(blocked);
    }

    public int limit() {
        return authProperties.getAnonymousLimit();
    }

}
