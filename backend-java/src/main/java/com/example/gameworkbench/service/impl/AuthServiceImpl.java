package com.example.gameworkbench.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        log.info("[认证] 注册开始 username={}", request.getUsername());
        Long sameUsernameCount = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        if (sameUsernameCount > 0) {
            log.warn("[认证] 注册失败：用户名已存在 username={}", request.getUsername());
            throw new BusinessException(40002, "用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUserUuid(UUID.randomUUID().toString());
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getUsername());
        user.setStatus(NORMAL_STATUS);
        user.setDeleted(0);

        sysUserMapper.insert(user);

        log.info("[认证] 注册成功 userId={} username={}", user.getId(), user.getUsername());
        return buildUserVO(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("[认证] 登录开始 username={}", request.getUsername());
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));

        if (user == null) {
            log.warn("[认证] 登录失败：用户不存在 username={}", request.getUsername());
            throw new BusinessException(40003, "用户名或密码错误");
        }
        if (!NORMAL_STATUS.equals(user.getStatus())) {
            log.warn("[认证] 登录失败：账号已被禁用 userId={} username={}", user.getId(), user.getUsername());
            throw new BusinessException(40004, "账号已被禁用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("[认证] 登录失败：用户名或密码错误 userId={} username={}", user.getId(), user.getUsername());
            throw new BusinessException(40003, "用户名或密码错误");
        }

        user.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(user);

        String token = jwtService.generateToken(user.getId(), user.getUsername());

        log.info("[认证] 登录成功 userId={} username={}", user.getId(), user.getUsername());
        return LoginResponse.builder()
                .token(token)
                .user(buildUserVO(user))
                .build();
    }

    @Override
    public UserVO me(Long userId) {
        if (userId == null) {
            log.warn("[认证] 获取当前用户失败：未登录请求");
            throw new BusinessException(40101, "请先登录");
        }

        log.info("[认证] 获取当前用户开始 userId={}", userId);
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            log.warn("[认证] 获取当前用户失败：用户不存在 userId={}", userId);
            throw new BusinessException(40401, "用户不存在");
        }
        if (!NORMAL_STATUS.equals(user.getStatus())) {
            log.warn("[认证] 获取当前用户失败：账号已被禁用 userId={} username={}", user.getId(), user.getUsername());
            throw new BusinessException(40004, "账号已被禁用");
        }

        log.info("[认证] 获取当前用户成功 userId={} username={}", user.getId(), user.getUsername());
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
