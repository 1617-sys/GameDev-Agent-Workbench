package com.example.gameworkbench.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.auth.LoginRequest;
import com.example.gameworkbench.dto.auth.RegisterRequest;
import com.example.gameworkbench.entity.SysUser;
import com.example.gameworkbench.mapper.SysUserMapper;
import com.example.gameworkbench.service.AuthService;
import com.example.gameworkbench.service.JwtService;
import com.example.gameworkbench.vo.auth.LoginResponse;
import com.example.gameworkbench.vo.auth.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String NORMAL_STATUS = "NORMAL";
    private static final String DEFAULT_ROLE = "USER";

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    public UserVO register(RegisterRequest request) {
        log.info("[Auth] register started username={}", request.getUsername());
        Long sameUsernameCount = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        if (sameUsernameCount > 0) {
            log.warn("[Auth] register rejected: username already exists username={}", request.getUsername());
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        SysUser user = new SysUser();
        user.setUserUuid(UUID.randomUUID().toString());
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getUsername());
        user.setStatus(NORMAL_STATUS);
        user.setDeleted(0);

        sysUserMapper.insert(user);

        log.info("[Auth] register succeeded userId={} username={}", user.getId(), user.getUsername());
        return buildUserVO(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("[Auth] login started username={}", request.getUsername());
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));

        if (user == null) {
            log.warn("[Auth] login rejected: user not found username={}", request.getUsername());
            throw new BusinessException(ErrorCode.INVALID_USERNAME_OR_PASSWORD);
        }
        if (!NORMAL_STATUS.equals(user.getStatus())) {
            log.warn("[Auth] login rejected: account disabled userId={} username={}", user.getId(), user.getUsername());
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("[Auth] login rejected: invalid password userId={} username={}", user.getId(), user.getUsername());
            throw new BusinessException(ErrorCode.INVALID_USERNAME_OR_PASSWORD);
        }

        user.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(user);

        String token = jwtService.generateToken(user.getId(), user.getUsername());

        log.info("[Auth] login succeeded userId={} username={}", user.getId(), user.getUsername());
        return LoginResponse.builder()
                .token(token)
                .user(buildUserVO(user))
                .build();
    }

    @Override
    public UserVO me(Long userId) {
        if (userId == null) {
            log.warn("[Auth] get current user rejected: unauthorized");
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        log.info("[Auth] get current user started userId={}", userId);
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            log.warn("[Auth] get current user rejected: user not found userId={}", userId);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!NORMAL_STATUS.equals(user.getStatus())) {
            log.warn("[Auth] get current user rejected: account disabled userId={} username={}", user.getId(), user.getUsername());
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        log.info("[Auth] get current user succeeded userId={} username={}", user.getId(), user.getUsername());
        return buildUserVO(user);
    }

    private UserVO buildUserVO(SysUser user) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(DEFAULT_ROLE)
                .build();
    }
}
