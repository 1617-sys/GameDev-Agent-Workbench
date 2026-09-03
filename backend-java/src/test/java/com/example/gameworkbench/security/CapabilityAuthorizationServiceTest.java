package com.example.gameworkbench.security;

import com.example.gameworkbench.entity.SysUser;
import com.example.gameworkbench.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CapabilityAuthorizationServiceTest {

    @Test
    void clientSuppliedAdminAuthorityCannotElevatePersistedUserRole() {
        SysUserMapper users = mock(SysUserMapper.class);
        SysUser user = user("USER");
        when(users.selectById(7L)).thenReturn(user);
        var service = new CapabilityAuthorizationService(users, new UserCapabilityService());
        var forged = new UsernamePasswordAuthenticationToken(
                7L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(service.has(forged, UserCapabilityService.ADMIN_DASHBOARD)).isFalse();
    }

    @Test
    void persistedAdminRoleAuthorizesAdminCapabilityWithoutClientAuthorities() {
        SysUserMapper users = mock(SysUserMapper.class);
        SysUser admin = user("ADMIN");
        when(users.selectById(9L)).thenReturn(admin);
        var service = new CapabilityAuthorizationService(users, new UserCapabilityService());
        var authentication = new UsernamePasswordAuthenticationToken(9L, null, List.of());

        assertThat(service.has(authentication, UserCapabilityService.ADMIN_DASHBOARD)).isTrue();
    }

    @Test
    void declaredCapabilitiesAreStableAndLayered() {
        var service = new UserCapabilityService();

        assertThat(service.forRole("USER"))
                .contains("projects.read", "generation.read", "generation.compile", "generation.build")
                .doesNotContain(UserCapabilityService.ADMIN_DASHBOARD, "generation.author", "generation.approve", "generation.release");
        assertThat(service.forRole("PROJECT_ADVANCED"))
                .contains("projects.read", "generation.read", "generation.release", "player-runs.create")
                .doesNotContain(UserCapabilityService.ADMIN_DASHBOARD);
        assertThat(service.forRole("ADMIN"))
                .contains(UserCapabilityService.ADMIN_DASHBOARD, "admin.diagnostics", "prompt-ops.manage", "admin.users.manage");
        assertThat(service.forRole("PROJECT_ADVANCED"))
                .doesNotContain("admin.users.manage");
    }

    private static SysUser user(String role) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setRole(role);
        user.setStatus("NORMAL");
        return user;
    }
}
