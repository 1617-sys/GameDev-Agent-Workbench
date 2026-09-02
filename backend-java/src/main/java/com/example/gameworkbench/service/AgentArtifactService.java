package com.example.gameworkbench.service;

import com.example.gameworkbench.vo.artifact.AgentArtifactVO;

import java.util.List;

public interface AgentArtifactService {

    List<AgentArtifactVO> listProjectArtifacts(Long userId, String projectUuid);

    AgentArtifactVO getArtifact(Long userId, String artifactUuid);

    AgentArtifactVO getProjectArtifact(Long userId, String projectUuid, String artifactUuid);
}
