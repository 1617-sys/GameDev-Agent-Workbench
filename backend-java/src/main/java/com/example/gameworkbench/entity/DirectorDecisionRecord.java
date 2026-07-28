package com.example.gameworkbench.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor @TableName("director_decision")
public class DirectorDecisionRecord {
    @TableId(type=IdType.AUTO) private Long id;
    private String decisionUuid;
    private Long directorRunId;
    private Long projectId;
    private Integer roundNumber;
    private Long stateVersion;
    private String kind;
    private String reasonSummary;
    private String decisionDigest;
    private String modelEvidenceJson;
    private String payloadJson;
    private LocalDateTime createdAt;
}
