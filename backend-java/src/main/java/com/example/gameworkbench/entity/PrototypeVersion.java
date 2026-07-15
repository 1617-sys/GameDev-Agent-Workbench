package com.example.gameworkbench.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("prototype_version")
public class PrototypeVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String versionUuid;
    private Long projectId;
    private Integer versionNumber;
    private String parentVersionUuid;
    private String source;
    private String gameConfigArtifactUuid;
    private String configDigest;
    private String runtimeCapabilityVersion;
    private Long createdBy;
    private String operation;
    private String idempotencyKey;
    private String requestFingerprint;
    private LocalDateTime createdAt;
}
