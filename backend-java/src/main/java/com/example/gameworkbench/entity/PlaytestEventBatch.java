package com.example.gameworkbench.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@TableName("playtest_event_batch")
public class PlaytestEventBatch {
    @TableId(type = IdType.AUTO) private Long id;
    private Long sessionId;
    private String batchUuid;
    private String batchDigest;
    private Integer acceptedCount;
    private LocalDateTime createdAt;
}
