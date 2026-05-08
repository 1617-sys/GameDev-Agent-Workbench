package com.example.gameworkbench.service;

import com.example.gameworkbench.dto.auth.LoginRequest;
import com.example.gameworkbench.dto.auth.RegisterRequest;
import com.example.gameworkbench.vo.auth.LoginResponse;
import com.example.gameworkbench.vo.auth.UserVO;

public interface AuthService {

    UserVO register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    UserVO me(Long userId);
}
