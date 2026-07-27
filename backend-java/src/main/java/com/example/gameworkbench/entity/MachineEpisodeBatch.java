package com.example.gameworkbench.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@TableName("machine_episode_batch")
public class MachineEpisodeBatch {
    @TableId(type = IdType.AUTO) private Long id;
    private String batchUuid;
    private Long userId;
    private Long projectId;
    private String clientBatchKey;
    private String idempotencyKey;
    private String requestFingerprint;
    private String episodeProtocolVersion;
    private String status;
    private Integer totalCount;
    private Integer completedCount;
    private Integer failedCount;
    private Integer rejectedCount;
    private Integer cancelledCount;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
