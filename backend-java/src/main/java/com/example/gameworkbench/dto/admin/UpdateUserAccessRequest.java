package com.example.gameworkbench.dto.admin;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
@Data public class UpdateUserAccessRequest { @Pattern(regexp="USER|PROJECT_ADVANCED|ADMIN", message="Invalid role") private String role; @Pattern(regexp="NORMAL|DISABLED", message="Invalid status") private String status; }
