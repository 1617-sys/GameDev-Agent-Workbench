package com.example.gameworkbench.controller;

import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.service.AgentArtifactService;
import com.example.gameworkbench.vo.artifact.AgentArtifactVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ArtifactController {

    private final AgentArtifactService agentArtifactService;

    @GetMapping("/projects/{projectUuid}/artifacts")
    public ApiResponse<List<AgentArtifactVO>> listProjectArtifacts(
            @AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid
    ) {
        return ApiResponse.success(agentArtifactService.listProjectArtifacts(userId, projectUuid));
    }

    @GetMapping("/artifacts/{artifactUuid}")
    public ApiResponse<AgentArtifactVO> getArtifact(
            @AuthenticationPrincipal Long userId,
            @PathVariable String artifactUuid
    ) {
        return ApiResponse.success(agentArtifactService.getArtifact(userId, artifactUuid));
    }
}
