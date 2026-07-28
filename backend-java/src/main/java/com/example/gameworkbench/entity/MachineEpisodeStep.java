package com.example.gameworkbench.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@TableName("machine_episode_step")
public class MachineEpisodeStep {
    @TableId(type = IdType.AUTO) private Long id;
    private Long episodeId;
    private Integer sequenceNumber;
    private Integer attemptNumber;
    private Integer simulationStepBefore;
    private Integer simulationStepAfter;
    private String observationDigest;
    private String requestedActionJson;
    private String transitionJson;
    private String stepJson;
    private Long rewardValueMicros;
}
