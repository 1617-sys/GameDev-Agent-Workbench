package com.example.gameworkbench.controller;

import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.dto.auth.LoginRequest;
import com.example.gameworkbench.dto.auth.RegisterRequest;
import com.example.gameworkbench.service.AuthService;
import com.example.gameworkbench.vo.auth.LoginResponse;
import com.example.gameworkbench.vo.auth.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> me(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(authService.me(userId));
    }
}
