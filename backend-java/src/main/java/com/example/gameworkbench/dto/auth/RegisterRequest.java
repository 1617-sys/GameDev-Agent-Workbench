package com.example.gameworkbench.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 4, max = 20, message = "Username length must be between 4 and 20")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 32, message = "Password length must be between 6 and 32")
    private String password;
}
