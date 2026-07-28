package com.example.gameworkbench.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor @TableName("director_run_event")
public class DirectorRunEvent {
    @TableId(type=IdType.AUTO) private Long id;
    private String eventUuid;
    private Long directorRunId;
    private Long projectId;
    private String eventType;
    private Long stateVersion;
    private String traceId;
    private String detailSummary;
    private LocalDateTime createdAt;
}
