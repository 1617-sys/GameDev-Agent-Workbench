package com.example.gameworkbench.security;

import com.example.gameworkbench.entity.SysUser;
import com.example.gameworkbench.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CapabilityAuthorizationService {

    private static final String NORMAL_STATUS = "NORMAL";

    private final SysUserMapper users;
    private final UserCapabilityService capabilities;

    public boolean has(Authentication authentication, String capability) {
        if (authentication == null || !authentication.isAuthenticated() || capability == null) return false;
        Long userId = principalUserId(authentication.getPrincipal());
        if (userId == null) return false;
        SysUser user = users.selectById(userId);
        return user != null
                && NORMAL_STATUS.equals(user.getStatus())
                && capabilities.forRole(user.getRole()).contains(capability);
    }

    private static Long principalUserId(Object principal) {
        if (principal instanceof Number number) return number.longValue();
        try {
            return principal == null ? null : Long.valueOf(principal.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
