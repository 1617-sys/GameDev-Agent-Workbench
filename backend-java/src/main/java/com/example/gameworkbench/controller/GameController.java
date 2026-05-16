package com.example.gameworkbench.controller;

import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.dto.gameProject.GameProjectRequest;
import com.example.gameworkbench.service.GameProjectService;
import com.example.gameworkbench.vo.project.GameProjectVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class GameController {

    private final GameProjectService gameProjectService;

    @PostMapping
    public ApiResponse<GameProjectVO> createProject(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GameProjectRequest request
    ) {
        return ApiResponse.success(gameProjectService.createProject(userId, request));
    }

    @GetMapping
    public ApiResponse<List<GameProjectVO>> listProjects(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(gameProjectService.listProjects(userId));
    }

    @GetMapping("/{projectUuid}")
    public ApiResponse<GameProjectVO> getProject(
            @AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid
    ) {
        return ApiResponse.success(gameProjectService.getProject(userId, projectUuid));
    }

    @PutMapping("/{projectUuid}")
    public ApiResponse<GameProjectVO> updateProject(
            @AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid,
            @Valid @RequestBody GameProjectRequest request
    ) {
        return ApiResponse.success(gameProjectService.updateProject(userId, projectUuid, request));
    }
}
