package com.example.gameworkbench.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@TableName("prototype_playtest_aggregate")
public class PrototypePlaytestAggregate {
    @TableId(type = IdType.AUTO) private Long id;
    private String prototypeVersionUuid;
    private Integer endedSessionCount;
    private Integer wonCount;
    private Integer lostCount;
    private Integer abandonedCount;
    private Long totalDurationMs;
    private Long totalScore;
    private Long totalHitCount;
    private Long totalCollectedCount;
    private Long totalRestartCount;
    private Integer healthDepletedCount;
    private Integer timeExpiredCount;
    private LocalDateTime snapshotAt;
}
