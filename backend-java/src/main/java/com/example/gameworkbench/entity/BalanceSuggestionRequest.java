package com.example.gameworkbench.entity;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor @TableName("balance_suggestion_request")
public class BalanceSuggestionRequest {
 @TableId(type=IdType.AUTO) private Long id; private Long userId; private Long projectId;
 private String prototypeVersionUuid; private String idempotencyKey; private String requestFingerprint;
 private String artifactUuid; private LocalDateTime createdAt;
}
