package com.example.gameworkbench.controller;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.entity.PrototypeExportJob;
import com.example.gameworkbench.service.PrototypeExportService;
import com.example.gameworkbench.vo.export.PrototypeExportJobVO;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/projects/{projectUuid}")
public class PrototypeExportController {
 private final PrototypeExportService service;
 @PostMapping("/prototype-versions/{versionUuid}/exports")
 public ApiResponse<PrototypeExportJobVO> create(@AuthenticationPrincipal Long userId,@PathVariable String projectUuid,@PathVariable String versionUuid,@RequestHeader("Idempotency-Key") String key){return ApiResponse.success(service.create(userId,projectUuid,versionUuid,key));}
 @GetMapping("/exports/{jobUuid}")
 public ApiResponse<PrototypeExportJobVO> get(@AuthenticationPrincipal Long userId,@PathVariable String projectUuid,@PathVariable String jobUuid){return ApiResponse.success(service.get(userId,projectUuid,jobUuid));}
 @PostMapping("/exports/{jobUuid}/retry")
 public ApiResponse<PrototypeExportJobVO> retry(@AuthenticationPrincipal Long userId,@PathVariable String projectUuid,@PathVariable String jobUuid){return ApiResponse.success(service.retry(userId,projectUuid,jobUuid));}
 @GetMapping("/exports/{jobUuid}/download")
 public ResponseEntity<byte[]> download(@AuthenticationPrincipal Long userId,@PathVariable String projectUuid,@PathVariable String jobUuid){PrototypeExportJob job=service.download(userId,projectUuid,jobUuid);ContentDisposition disposition=ContentDisposition.attachment().filename(job.getPackageName(),StandardCharsets.UTF_8).build();return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip")).header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString()).contentLength(job.getPackageSize()).body(job.getPackageBytes());}
}
