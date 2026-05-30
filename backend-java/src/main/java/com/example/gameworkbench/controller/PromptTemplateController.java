package com.example.gameworkbench.controller;

import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.dto.promptTemplate.PromptTemplateRequest;
import com.example.gameworkbench.service.PromptTemplateService;
import com.example.gameworkbench.vo.prompt.PromptTemplateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @PutMapping("/{templateUuid}")
    public ApiResponse<PromptTemplateVO> updatePromptTemplate(
            @AuthenticationPrincipal Long userId,
            @PathVariable String templateUuid,
            @Valid @RequestBody PromptTemplateRequest request
    ) {
        return ApiResponse.success(promptTemplateService.updatePromptTemplate(userId, templateUuid, request));
    }

    @GetMapping("/get")
    public ApiResponse<PromptTemplateVO> getPromptTemplate(
            @AuthenticationPrincipal Long userId,
            @RequestParam String templateUuid
    ) {
        return ApiResponse.success(promptTemplateService.getPromptTemplate(userId, templateUuid));
    }
}
