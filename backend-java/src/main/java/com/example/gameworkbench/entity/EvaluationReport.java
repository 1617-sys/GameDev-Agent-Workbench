package com.example.gameworkbench.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("evaluation_report")
public class EvaluationReport {
 @TableId(type=IdType.AUTO) private Long id;
 private Long artifactId; private String evaluatorType; private String status; private String schemaKey; private String schemaVersion;
 private String inputHash; private String violationsJson; private Integer evaluationAttempt; private LocalDateTime evaluatedAt;
 private String ruleVersion;
}
