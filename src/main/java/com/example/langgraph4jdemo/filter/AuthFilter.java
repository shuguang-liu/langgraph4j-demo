package com.example.langgraph4jdemo.filter;

import com.example.langgraph4jdemo.dto.ApiError;
import com.example.langgraph4jdemo.service.AnonymousQuotaService;
import com.example.langgraph4jdemo.service.AuthService;
import com.example.langgraph4jdemo.util.ClientIpUtils;
import com.example.langgraph4jdemo.web.CachedBodyRequestWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * @author liushug
 * @description 业务接口统一鉴权（token + threadId 双要素）：
 * <pre>
 * 1. 请求头带有效 token（Authorization: Bearer xxx / X-Auth-Token）→ 已登录：
 *    身份与永久 threadId 都从 token 解出，body 里的 threadId 必须与之一致（或缺失时自动回写）
 * 2. 不带 token → 未登录：客户端 IP 即 threadId，body 里即使带了别人的 threadId 也会被
 *    静默覆盖为 IP（不给攻击者"探测 threadId 是否有效"的口子），按 IP 限次（默认 5 次）
 * </pre>
 * dev 模式（激活 profile 含 dev）下整体跳过：不验 token、不限次，threadId 由请求体自行指定
 * （缺失时回退为 IP），仅用于本地联调。
 * 最终 threadId 会回写到请求体，业务 Controller 无需重复处理。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Component
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_HEADER = "X-Auth-Token";

    private final AuthService authService;
    private final AnonymousQuotaService anonymousQuotaService;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    /** dev 模式：跳过 token 校验与限次 */
    private boolean devMode;

    @PostConstruct
    void initProfileMode() {
        devMode = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        if (devMode) {
            log.warn("[auth] 当前为 dev 模式：已跳过 token 校验与未登录限次，threadId 由请求体自行指定");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 只保护业务接口，登录接口本身放行
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        request.setCharacterEncoding(StandardCharsets.UTF_8.name());

        if (devMode) {
            doDevPassThrough(request, response, chain);
            return;
        }

        // 1. 解析 token：无效 token 直接拒绝，避免过期 token 降级成匿名消耗限次额度
        String token = resolveToken(request);
        AuthService.TokenInfo tokenInfo = null;
        if (token != null) {
            tokenInfo = authService.resolveToken(token).orElse(null);
            if (tokenInfo == null) {
                writeError(response, HttpStatus.UNAUTHORIZED, "token 无效或已过期，请重新登录");
                return;
            }
        }

        // 2. 缓存并解析请求体
        byte[] rawBody = request.getInputStream().readAllBytes();
        ObjectNode bodyJson = null;
        if (rawBody.length > 0) {
            JsonNode parsed;
            try {
                parsed = objectMapper.readTree(rawBody);
            } catch (IOException e) {
                writeError(response, HttpStatus.BAD_REQUEST, "请求体不是合法的 JSON");
                return;
            }
            if (parsed instanceof ObjectNode obj) {
                bodyJson = obj;
            }
        }
        String bodyThreadId = bodyJson == null ? null : optionalText(bodyJson, "threadId");

        // 3. 确定本次请求的 threadId
        String effectiveThreadId;
        if (tokenInfo != null) {
            // 已登录：threadId 以 token 内嵌的永久 threadId 为准，body 里的必须一致
            effectiveThreadId = tokenInfo.threadId();
            if (bodyThreadId != null && !bodyThreadId.equals(effectiveThreadId)) {
                writeError(response, HttpStatus.UNAUTHORIZED, "threadId 与当前登录用户不匹配，请使用登录时返回的 threadId");
                return;
            }
        } else {
            // 未登录：IP 就是 threadId，body 里带的任何 threadId 一律忽略并覆盖
            String ip = ClientIpUtils.resolve(request);
            if (!anonymousQuotaService.tryAcquire(ip)) {
                writeError(response, HttpStatus.TOO_MANY_REQUESTS,
                        ("未登录调用次数已超出上限（%d 次），该 IP 已被永久限流，请登录后使用")
                                .formatted(anonymousQuotaService.limit()));
                return;
            }
            effectiveThreadId = ip;
            log.info("[auth] 未登录请求，使用 IP 作为 threadId: ip={}, used<={} ", ip, anonymousQuotaService.limit());
        }

        // 4. 把最终 threadId 回写进请求体，业务 Controller 直接使用
        HttpServletRequest requestToUse = request;
        if (bodyJson != null) {
            bodyJson.put("threadId", effectiveThreadId);
            requestToUse = new CachedBodyRequestWrapper(request, objectMapper.writeValueAsBytes(bodyJson));
        }
        chain.doFilter(requestToUse, response);
    }

    /**
     * dev 模式直通：不验 token、不限次；threadId 用请求体里的值，
     * 缺失/为空时回退为 IP（避免下游图执行时空指针），非 JSON 请求体原样放行
     */
    private void doDevPassThrough(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] rawBody = request.getInputStream().readAllBytes();
        ObjectNode bodyJson = null;
        if (rawBody.length > 0) {
            JsonNode parsed;
            try {
                parsed = objectMapper.readTree(rawBody);
            } catch (IOException e) {
                chain.doFilter(request, response);
                return;
            }
            if (parsed instanceof ObjectNode obj) {
                bodyJson = obj;
            }
        }
        String bodyThreadId = bodyJson == null ? null : optionalText(bodyJson, "threadId");
        HttpServletRequest requestToUse = request;
        if (bodyJson != null) {
            // 请求体已被读取，必须用缓存包装器回供；threadId 缺失时回填 IP
            if (bodyThreadId == null || bodyThreadId.isBlank()) {
                bodyJson.put("threadId", ClientIpUtils.resolve(request));
            }
            requestToUse = new CachedBodyRequestWrapper(request, objectMapper.writeValueAsBytes(bodyJson));
        }
        chain.doFilter(requestToUse, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return Optional.ofNullable(request.getHeader(TOKEN_HEADER))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse(null);
    }

    private String optionalText(ObjectNode json, String field) {
        JsonNode node = json.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(new ApiError(status.value(), message)));
    }

}
