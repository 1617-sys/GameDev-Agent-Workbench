package com.example.gameworkbench.controller;
import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*; import org.springframework.web.multipart.MultipartFile;
import com.example.gameworkbench.common.ApiResponse; import com.example.gameworkbench.dto.knowledge.KnowledgeUploadResponse; import com.example.gameworkbench.service.KnowledgeDocumentIngestionService;
import lombok.RequiredArgsConstructor;
@RestController @RequestMapping("/api/projects/{projectUuid}/knowledge-documents") @RequiredArgsConstructor public class KnowledgeDocumentController { private final KnowledgeDocumentIngestionService ingestion; @PostMapping(consumes="multipart/form-data") public ApiResponse<KnowledgeUploadResponse> upload(@AuthenticationPrincipal Long userId,@PathVariable String projectUuid,@RequestPart("file") MultipartFile file){ return ApiResponse.success(ingestion.upload(userId,projectUuid,file)); } }
