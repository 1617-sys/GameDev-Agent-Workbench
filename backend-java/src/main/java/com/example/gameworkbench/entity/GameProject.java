package com.example.gameworkbench.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("game_project")
public class GameProject {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String projectUuid;

    private Long userId;

    private String name;

    private String gameType;

    private String targetPlatform;

    private String description;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
