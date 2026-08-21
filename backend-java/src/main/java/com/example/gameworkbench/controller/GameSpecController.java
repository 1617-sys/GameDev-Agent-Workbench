package com.example.gameworkbench.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.dto.gamespec.CompileGameSpecRequest;
import com.example.gameworkbench.dto.gamespec.AuthorGameSpecRequest;
import com.example.gameworkbench.gamespec.GameSpecApplicationService;
import com.example.gameworkbench.gamespec.GameSpecCompilationResult;
import com.example.gameworkbench.gamespec.SpecAuthorResult;
import com.example.gameworkbench.gamespec.SpecAuthorService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v5")
public class GameSpecController {
    private final GameSpecApplicationService service;
    private final SpecAuthorService specAuthor;

    @GetMapping("/gamespec/capabilities")
    public ApiResponse<ObjectNode> capabilities() {
        return ApiResponse.success(service.capabilities());
    }

    @PostMapping("/projects/{projectUuid}/gamespec/compile")
    public ApiResponse<GameSpecCompilationResult> compile(
            @AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid,
            @Valid @RequestBody CompileGameSpecRequest request) {
        return ApiResponse.success(service.compile(userId, projectUuid, request.spec()));
    }

    @PostMapping("/projects/{projectUuid}/gamespec/author")
    public ApiResponse<SpecAuthorResult> author(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid, @Valid @RequestBody AuthorGameSpecRequest request) {
        return ApiResponse.success(specAuthor.author(userId, projectUuid, request.idea(), request.currentSpec()));
    }
}
