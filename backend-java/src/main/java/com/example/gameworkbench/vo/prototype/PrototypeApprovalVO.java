package com.example.gameworkbench.vo.prototype;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value @Builder
public class PrototypeApprovalVO {
    String approvalUuid;
    String prototypeVersionUuid;
    String directorRunUuid;
    Long actorUserId;
    String actorType;
    String decision;
    String reason;
    LocalDateTime createdAt;
    boolean reused;
}
