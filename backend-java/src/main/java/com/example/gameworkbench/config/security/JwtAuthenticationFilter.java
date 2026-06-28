package com.example.gameworkbench.config.security;

import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行 JWT 认证过滤逻辑，从请求头中提取 Bearer Token 并进行校验。
     *
     * <p>处理流程：提取 Authorization 头中的 JWT → 校验 Token 有效性 → 解析用户 ID
     * → 将认证信息写入 SecurityContext → 继续执行过滤器链。</p>
     *
     * @param request     HTTP 请求对象，期望其 Authorization 头携带 "Bearer &lt;token&gt;" 格式的 JWT
     * @param response    HTTP 响应对象，校验失败时用于写入 401 未授权响应
     * @param filterChain 过滤器链，认证通过或无需认证时将继续传递给后续过滤器
     * @throws ServletException 过滤器链执行过程中可能抛出的 Servlet 异常
     * @throws IOException      读取请求或写入响应时可能抛出的 IO 异常
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // 从 Authorization 头中提取 Bearer Token，缺失或格式不符则跳过认证
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7);

        // 校验 Token 是否合法（签名、有效期等），不合法则返回 401
        if (!jwtService.validateToken(token)) {
            writeUnauthorized(response);
            return;
        }

        // 从 Token 中解析用户 ID，解析失败则返回 401
        Long userId = jwtService.parseUserId(token);
        if (userId == null) {
            writeUnauthorized(response);
            return;
        }

        // 将用户 ID 封装为认证凭据并写入 Spring Security 上下文
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, null);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(
                        ErrorCode.TOKEN_INVALID_OR_EXPIRED.getCode(),
                        ErrorCode.TOKEN_INVALID_OR_EXPIRED.getMessage()
                )
        ));
    }
}
