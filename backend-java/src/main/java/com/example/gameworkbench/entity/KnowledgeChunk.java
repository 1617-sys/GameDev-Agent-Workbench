package com.example.gameworkbench.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {
    @TableId(type = IdType.AUTO) private Long id;
    private String chunkUuid;
    private Long documentId;
    private Long projectId;
    private Integer ordinal;
    private String textRef;
    private String textHash;
    private Integer tokenCount;
    private String chunkingVersion;
    private String embeddingModel;
    private String indexStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic private Integer deleted;
}
