package com.example.gameworkbench.vo.admin;
import lombok.Builder; import lombok.Data; import java.time.LocalDateTime;
@Data @Builder public class AdminUserVO { private Long id; private String username; private String role; private String status; private LocalDateTime createdAt; private LocalDateTime lastLoginAt; private boolean self; }
