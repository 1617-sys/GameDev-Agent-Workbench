package com.example.gameworkbench.service;

import java.util.List;
import com.example.gameworkbench.dto.player.CreatePlayerRunRequest;
import com.example.gameworkbench.vo.player.PlayerRunVO;

public interface PlayerRunService {
    PlayerRunVO submit(Long userId, String projectUuid, String idempotencyKey, String traceId, CreatePlayerRunRequest request);
    PlayerRunVO get(Long userId, String projectUuid, String runUuid);
    List<PlayerRunVO> list(Long userId, String projectUuid, String versionUuid);
}
