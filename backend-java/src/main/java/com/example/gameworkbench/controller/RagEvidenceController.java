package com.example.gameworkbench.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.dto.retrieval.RagEvidenceResponse;
import com.example.gameworkbench.service.RagEvidenceReadService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/workflow-runs")
@RequiredArgsConstructor
public class RagEvidenceController {

    private final RagEvidenceReadService evidence;

    @GetMapping("/{workflowRunUuid}/rag-evidence")
    public ApiResponse<List<RagEvidenceResponse>> list(
            @AuthenticationPrincipal Long userId,
            @PathVariable String workflowRunUuid
    ) {
        return ApiResponse.success(evidence.list(userId, workflowRunUuid));
    }
}
