package com.rag.controller;

import com.rag.dto.LoginRequest;
import com.rag.dto.RegisterRequest;
import com.rag.service.UserService;
import com.rag.vo.LoginResponse;
import com.rag.vo.Result;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 提供用户注册和登录接口，返回 JWT Token
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    /**
     * 用户注册
     * 创建新用户并返回 JWT Token
     */
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        LoginResponse response = userService.register(request);
        return Result.success(response);
    }

    /**
     * 用户登录
     * 验证用户名密码并返回 JWT Token
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return Result.success(response);
    }
}
