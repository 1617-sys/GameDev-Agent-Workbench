package com.example.gameworkbench.controller;

import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.dto.promptTemplate.PromptTemplateRequest;
import com.example.gameworkbench.service.PromptTemplateService;
import com.example.gameworkbench.vo.prompt.PromptTemplateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/promptTemplate")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    @PostMapping("/modify")
    public ApiResponse<PromptTemplateVO> modifyPromptTemplate(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PromptTemplateRequest request
    ) {
        return ApiResponse.success(promptTemplateService.modifyPromptTemplate(userId, request));
    }

    @GetMapping("/get")
    public ApiResponse<PromptTemplateVO> getPromptTemplate(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PromptTemplateRequest request
    ) {
        return ApiResponse.success(promptTemplateService.getPromptTemplate(userId, request));
    }
}
