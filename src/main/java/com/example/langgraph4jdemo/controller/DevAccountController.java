package com.example.langgraph4jdemo.controller;

import com.example.langgraph4jdemo.dto.AccountCreated;
import com.example.langgraph4jdemo.dto.ApiError;
import com.example.langgraph4jdemo.dto.LoginRequest;
import com.example.langgraph4jdemo.exception.AccountExistsException;
import com.example.langgraph4jdemo.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author liushug
 * @description 仅 dev 环境存在的账号管理接口：@Profile("dev") 使得 prod 下整个 Controller 不注册（404）。
 * 用于开发期创建账号，无需登录（解决没有账号时无法登录的鸡生蛋问题）。
 */
@Slf4j
@Profile("dev")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth/dev")
public class DevAccountController {

    private final AuthService authService;

    /**
     * 创建账号：随机永久 threadId + SM3 密码散列，直接写入 auth 库 t_user
     */
    @PostMapping("/account")
    public ResponseEntity<?> create(@RequestBody LoginRequest request) {
        try {
            AccountCreated created = authService.createAccount(request.getUsername(), request.getPassword());
            log.warn("[dev] 通过 dev 专用接口创建账号: {}", created.username());
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (AccountExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError(409, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError(400, e.getMessage()));
        }
    }

}
