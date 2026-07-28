package com.example.gameworkbench.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor @TableName("prototype_approval")
public class PrototypeApproval {
    @TableId(type=IdType.AUTO) private Long id;
    private String approvalUuid;
    private Long projectId;
    private String prototypeVersionUuid;
    private String directorRunUuid;
    private Long actorUserId;
    private String actorType;
    private String decision;
    private String reason;
    private String idempotencyKey;
    private String requestFingerprint;
    private LocalDateTime createdAt;
}
