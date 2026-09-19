package com.example.langgraph4jdemo.service;

import com.example.langgraph4jdemo.config.AuthProperties;
import com.example.langgraph4jdemo.dto.AccountCreated;
import com.example.langgraph4jdemo.dto.LoginResponse;
import com.example.langgraph4jdemo.exception.AccountExistsException;
import com.example.langgraph4jdemo.exception.AuthException;
import com.example.langgraph4jdemo.util.Sm3Util;
import com.example.langgraph4jdemo.util.Sm4Util;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @author liushug
 * @description 登录鉴权（第二个数据源 auth 库 + Redis），token + threadId 双要素：
 * <p>
 * - 账号：启动时由 AuthDataInitializer 建表并写入内置账号，密码存 SM3 散列
 * - threadId：建账号时随机生成（u_ + UUID），只存在 t_user.thread_id 里，永久不变；
 *   不由用户名推导，密钥泄露也推算不出任何人的 threadId
 * - token：SM4 加密 Base64Url(IV + username|threadId|expireAt|uuid)；
 *   登录时同步写入 Redis（key = SM3(token)，value = 身份信息，TTL = 有效期），
 *   校验以 Redis 为唯一事实源 → 支持登出/吊销，重启不丢，Redis 自动过期免清理
 */
@Service
public class AuthService {

    private static final String PAYLOAD_SEPARATOR = "|";
    private static final String TOKEN_KEY_PREFIX = "auth:token:";
    private static final Pattern HEX_128BIT = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final Pattern USERNAME_FORBIDDEN = Pattern.compile("[|\\r\\n]");

    private final AuthProperties authProperties;
    private final JdbcTemplate authJdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private byte[] key;

    public AuthService(AuthProperties authProperties,
                       @Qualifier("authJdbcTemplate") JdbcTemplate authJdbcTemplate,
                       StringRedisTemplate stringRedisTemplate,
                       ObjectMapper objectMapper) {
        this.authProperties = authProperties;
        this.authJdbcTemplate = authJdbcTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        this.key = parseSm4Key(authProperties.getSm4Key());
        for (AuthProperties.UserAccount user : authProperties.getUsers()) {
            if (user.getUsername() == null || USERNAME_FORBIDDEN.matcher(user.getUsername()).find()) {
                throw new IllegalStateException("内置账号的用户名不能包含 |、换行等字符: " + user.getUsername());
            }
        }
    }

    /**
     * 密钥支持两种写法：32 位 hex（推荐，随机字节）或 16 字符的字符串，都必须等价于 16 字节
     */
    private byte[] parseSm4Key(String sm4Key) {
        if (sm4Key == null || sm4Key.isBlank()) {
            throw new IllegalStateException("未配置 SM4 密钥：请设置环境变量 AUTH_SM4_KEY（32 位 hex，可用 openssl rand -hex 16 生成）");
        }
        byte[] keyBytes = HEX_128BIT.matcher(sm4Key.trim()).matches()
                ? HexFormat.of().parseHex(sm4Key.trim())
                : sm4Key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 16) {
            throw new IllegalStateException("SM4 密钥长度必须是 16 字节：32 位 hex 或 16 字符的字符串");
        }
        return keyBytes;
    }

    /**
     * 登录校验（查 auth 库）→ 生成 SM4 加密的 token 并写入 Redis，threadId 取库里该账号的永久 threadId
     */
    public LoginResponse login(String username, String password) {
        if (username == null || password == null) {
            throw new AuthException("用户名或密码错误");
        }
        List<UserRecord> rows = authJdbcTemplate.query(
                "SELECT username, password_hash, thread_id FROM t_user WHERE username = ?",
                (rs, i) -> new UserRecord(rs.getString("username"), rs.getString("password_hash"), rs.getString("thread_id")),
                username);
        if (rows.isEmpty() || !Sm3Util.hashHex(password).equals(rows.get(0).passwordHash())) {
            throw new AuthException("用户名或密码错误");
        }
        UserRecord user = rows.get(0);
        // expireAt=0 表示永久；uuid 保证同一用户每次登录生成的 token 都不同
        long expireAt = authProperties.getTokenExpireMinutes() <= 0
                ? 0L
                : System.currentTimeMillis() + authProperties.getTokenExpireMinutes() * 60_000L;
        String payload = String.join(PAYLOAD_SEPARATOR, user.username(), user.threadId(), String.valueOf(expireAt), UUID.randomUUID().toString());
        String token = Sm4Util.encryptToBase64Url(key, payload.getBytes(StandardCharsets.UTF_8));
        saveToken(token, user.username(), user.threadId());
        return new LoginResponse(token, user.threadId(), user.username(), expireAt);
    }

    /**
     * 校验 token：以 Redis 是否存在为准（登出/吊销过的 token 直接失效）。
     * Redis 故障时抛出异常（基础设施问题应表现为 500 而不是 401）
     */
    public Optional<TokenInfo> resolveToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String value = stringRedisTemplate.opsForValue().get(tokenKey(token));
        if (value == null) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            String username = node.path("username").asText(null);
            String threadId = node.path("threadId").asText(null);
            if (username == null || threadId == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenInfo(username, threadId));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * 登出/吊销：删除 Redis 里的 token 记录，该 token 立即失效
     */
    public void revokeToken(String token) {
        stringRedisTemplate.delete(tokenKey(token));
    }

    /**
     * 创建账号（仅 dev 环境接口使用）：随机永久 threadId + SM3 密码散列，直接写入 auth 库
     *
     * @throws AccountExistsException 用户名已存在
     * @throws IllegalArgumentException 用户名/密码不合法
     */
    public AccountCreated createAccount(String username, String password) {
        if (username == null || username.isBlank() || username.length() > 64) {
            throw new IllegalArgumentException("用户名不能为空且长度不超过 64");
        }
        if (USERNAME_FORBIDDEN.matcher(username).find()) {
            throw new IllegalArgumentException("用户名不能包含 |、换行等字符");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (accountExists(username)) {
            throw new AccountExistsException(username);
        }
        String threadId = newThreadId();
        try {
            authJdbcTemplate.update(
                    "INSERT INTO t_user(username, password_hash, thread_id) VALUES (?, ?, ?)",
                    username, Sm3Util.hashHex(password), threadId);
        } catch (DuplicateKeyException e) {
            // 并发插入撞上唯一约束的兜底
            throw new AccountExistsException(username);
        }
        return new AccountCreated(username, threadId);
    }

    private boolean accountExists(String username) {
        Integer count = authJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_user WHERE username = ?", Integer.class, username);
        return count != null && count > 0;
    }

    /**
     * 新账号的永久 threadId：随机生成（u_ + 带横杠 UUID），只存库，不与用户名有任何推导关系。
     * 带横杠的格式与旧版"u_ + 32位连续hex（SM4(用户名)）"可区分，便于一次性迁移
     */
    public String newThreadId() {
        return "u_" + UUID.randomUUID();
    }

    /**
     * token 写入 Redis：key 用 SM3(token)（避免原始 token 出现在 Redis 键里），value 存身份与 threadId，
     * TTL = token 有效期（0 = 永久，不设 TTL）
     */
    private void saveToken(String token, String username, String threadId) {
        try {
            String value = objectMapper.writeValueAsString(Map.of("username", username, "threadId", threadId));
            long minutes = authProperties.getTokenExpireMinutes();
            if (minutes > 0) {
                stringRedisTemplate.opsForValue().set(tokenKey(token), value, Duration.ofMinutes(minutes));
            } else {
                stringRedisTemplate.opsForValue().set(tokenKey(token), value);
            }
        } catch (DataAccessException e) {
            throw new IllegalStateException("token 写入 Redis 失败，请检查 Redis 连接（REDIS_HOST/REDIS_PORT/REDIS_PASSWORD）", e);
        } catch (IOException e) {
            throw new IllegalStateException("token 信息序列化失败", e);
        }
    }

    private String tokenKey(String token) {
        return TOKEN_KEY_PREFIX + Sm3Util.hashHex(token);
    }

    public record TokenInfo(String username, String threadId) {
    }

    private record UserRecord(String username, String passwordHash, String threadId) {
    }

}
