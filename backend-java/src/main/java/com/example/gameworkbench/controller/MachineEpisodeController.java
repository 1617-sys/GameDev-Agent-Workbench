package com.example.gameworkbench.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.dto.episode.PersistMachineEpisodeBatchRequest;
import com.example.gameworkbench.service.MachineEpisodeService;
import com.example.gameworkbench.vo.episode.MachineEpisodeAggregateVO;
import com.example.gameworkbench.vo.episode.MachineEpisodeBatchVO;
import com.example.gameworkbench.vo.episode.MachineEpisodeVO;
import com.example.gameworkbench.vo.episode.MachineEpisodeStepPageVO;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectUuid}/machine-episodes")
@org.springframework.security.access.prepost.PreAuthorize("@capabilityAuthorizationService.has(authentication, 'machine-episodes.read')")
public class MachineEpisodeController {
    private final MachineEpisodeService service;

    @PostMapping("/batches")
    @org.springframework.security.access.prepost.PreAuthorize("denyAll()")
    public ApiResponse<MachineEpisodeBatchVO> persist(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid, @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PersistMachineEpisodeBatchRequest request) {
        return ApiResponse.success(service.persistBatch(userId, projectUuid, idempotencyKey, request));
    }

    @GetMapping("/batches/{batchUuid}")
    public ApiResponse<MachineEpisodeBatchVO> batch(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid, @PathVariable String batchUuid) {
        return ApiResponse.success(service.getBatch(userId, projectUuid, batchUuid));
    }

    @GetMapping("/{episodeUuid}")
    public ApiResponse<MachineEpisodeVO> episode(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid, @PathVariable String episodeUuid) {
        return ApiResponse.success(service.getEpisode(userId, projectUuid, episodeUuid));
    }

    @GetMapping("/{episodeUuid}/steps")
    public ApiResponse<MachineEpisodeStepPageVO> steps(@AuthenticationPrincipal Long userId,@PathVariable String projectUuid,@PathVariable String episodeUuid,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){return ApiResponse.success(service.getSteps(userId,projectUuid,episodeUuid,page,size));}

    @GetMapping("/{episodeUuid}/summary")
    public ApiResponse<MachineEpisodeVO> summary(@AuthenticationPrincipal Long userId,@PathVariable String projectUuid,@PathVariable String episodeUuid){return ApiResponse.success(service.getEpisodeSummary(userId,projectUuid,episodeUuid));}

    @GetMapping("/prototype-versions/{versionUuid}/aggregate")
    public ApiResponse<MachineEpisodeAggregateVO> aggregate(@AuthenticationPrincipal Long userId,
            @PathVariable String projectUuid, @PathVariable String versionUuid) {
        return ApiResponse.success(service.aggregate(userId, projectUuid, versionUuid));
    }
}
