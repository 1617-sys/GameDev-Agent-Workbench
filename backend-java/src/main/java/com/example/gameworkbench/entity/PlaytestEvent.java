package com.example.gameworkbench.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@TableName("playtest_event")
public class PlaytestEvent {
    @TableId(type = IdType.AUTO) private Long id;
    private String eventUuid;
    private Long sessionId;
    private Integer sequenceNumber;
    private String eventType;
    private Integer clientElapsedMs;
    private String payloadJson;
    private String eventDigest;
    private LocalDateTime receivedAt;
}
