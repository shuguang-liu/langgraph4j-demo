package com.example.langgraph4jdemo.controller;

import com.example.langgraph4jdemo.dto.ApiError;
import com.example.langgraph4jdemo.dto.LoginRequest;
import com.example.langgraph4jdemo.dto.LoginResponse;
import com.example.langgraph4jdemo.exception.AuthException;
import com.example.langgraph4jdemo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @author liushug
 * @description 登录接口（不做注册，账号由 auth 库内置）
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * 登录成功返回 SM4 加密的 token（服务端存 Redis，可吊销）和永久 threadId，
     * 后续业务接口必须携带 Authorization: Bearer <token> + body 里的 threadId
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request.getUsername(), request.getPassword());
            return ResponseEntity.ok(response);
        } catch (AuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiError(HttpStatus.UNAUTHORIZED.value(), e.getMessage()));
        }
    }

    /**
     * 登出：删除 Redis 中的 token 记录，该 token 立即失效
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String token = resolveHeaderToken(request);
        if (token == null) {
            return ResponseEntity.badRequest()
                    .body(new ApiError(400, "请求头未携带 token（Authorization: Bearer <token> 或 X-Auth-Token）"));
        }
        authService.revokeToken(token);
        return ResponseEntity.ok(Map.of("code", 200, "message", "已退出登录，token 已作废"));
    }

    private String resolveHeaderToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        String xToken = request.getHeader("X-Auth-Token");
        return xToken == null || xToken.isBlank() ? null : xToken.trim();
    }

}
