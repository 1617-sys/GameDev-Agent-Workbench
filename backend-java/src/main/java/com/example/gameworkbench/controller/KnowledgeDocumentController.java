package com.example.gameworkbench.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.dto.knowledge.KnowledgeLibraryResponse;
import com.example.gameworkbench.dto.knowledge.KnowledgeUploadResponse;
import com.example.gameworkbench.service.KnowledgeDocumentIngestionService;
import com.example.gameworkbench.service.KnowledgeDocumentReadService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/projects/{projectUuid}/knowledge-documents")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeDocumentIngestionService ingestion;
    private final KnowledgeDocumentReadService readService;

    @GetMapping
    public ApiResponse<KnowledgeLibraryResponse> list(
            @AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid
    ) {
        return ApiResponse.success(readService.list(userId, projectUuid));
    }

    @PostMapping(consumes = "multipart/form-data")
    @org.springframework.security.access.prepost.PreAuthorize("@capabilityAuthorizationService.has(authentication, 'knowledge.upload')")
    public ApiResponse<KnowledgeUploadResponse> upload(
            @AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(ingestion.upload(userId, projectUuid, file));
    }
}
