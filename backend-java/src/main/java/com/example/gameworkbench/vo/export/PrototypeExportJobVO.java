package com.example.gameworkbench.vo.export;
import java.time.LocalDateTime;
import lombok.*;
@Data @Builder
public class PrototypeExportJobVO {
 private String jobUuid; private String prototypeVersionUuid; private String status; private String packageName;
 private String packageDigest; private Long packageSize; private Integer attemptCount; private String errorCode;
 private LocalDateTime createdAt; private LocalDateTime completedAt; private boolean reused;
}
