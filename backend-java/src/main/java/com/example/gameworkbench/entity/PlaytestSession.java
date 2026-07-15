package com.example.gameworkbench.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@TableName("playtest_session")
public class PlaytestSession {
    @TableId(type = IdType.AUTO) private Long id;
    private String sessionUuid;
    private Long userId;
    private Long projectId;
    private String prototypeVersionUuid;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime closeAfter;
    private Integer lastSequence;
    private Integer eventCount;
    private String outcome;
    private String failureReason;
    private Integer score;
    private Integer durationMs;
    private Integer hitCount;
    private Integer collectedCount;
    private Integer restartCount;
}
