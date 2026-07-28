package com.example.gameworkbench.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor @TableName("experiment_candidate")
public class ExperimentCandidate {
    @TableId(type=IdType.AUTO) private Long id;
    private String candidateUuid;
    private Long directorRunId;
    private Long projectId;
    private Integer ordinalNumber;
    private String status;
    private String prototypeVersionUuid;
    private String playerRunUuid;
    private String machineEpisodeUuid;
    private String configDigest;
    private String evidenceJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
