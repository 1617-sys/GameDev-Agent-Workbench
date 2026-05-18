package com.example.gameworkbench.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.service.AgentArtifactService;
import com.example.gameworkbench.vo.artifact.AgentArtifactVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentArtifactServiceImpl implements AgentArtifactService {

    private final AgentArtifactMapper agentArtifactMapper;
    private final GameProjectMapper gameProjectMapper;

    @Override
    public List<AgentArtifactVO> listProjectArtifacts(Long userId, String projectUuid) {
        if (userId == null) {
            log.warn("[产物] 查询项目产物列表失败：未登录请求 projectUuid={}", projectUuid);
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        GameProject gameProject = gameProjectMapper.selectOne(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getProjectUuid, projectUuid)
                .eq(GameProject::getUserId, userId));
        if (gameProject == null) {
            log.warn("[产物] 查询项目产物列表失败：项目不存在或无权访问 userId={} projectUuid={}", userId, projectUuid);
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        log.info("[产物] 查询项目产物列表开始 userId={} projectId={} projectUuid={}",
                userId, gameProject.getId(), projectUuid);
        List<AgentArtifact> artifacts = agentArtifactMapper.selectList(new LambdaQueryWrapper<AgentArtifact>()
                .eq(AgentArtifact::getProjectId, gameProject.getId())
                .orderByDesc(AgentArtifact::getCreatedAt));
        log.info("[产物] 查询项目产物列表成功 userId={} projectId={} count={}",
                userId, gameProject.getId(), artifacts.size());
        return artifacts.stream().map(this::toVO).toList();
    }

    @Override
    public AgentArtifactVO getArtifact(Long userId, String artifactUuid) {
        if (userId == null) {
            log.warn("[产物] 查询产物详情失败：未登录请求 artifactUuid={}", artifactUuid);
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        AgentArtifact artifact = agentArtifactMapper.selectOne(new LambdaQueryWrapper<AgentArtifact>()
                .eq(AgentArtifact::getArtifactUuid, artifactUuid));
        if (artifact == null) {
            log.warn("[产物] 查询产物详情失败：产物不存在 userId={} artifactUuid={}", userId, artifactUuid);
            throw new BusinessException(ErrorCode.ARTIFACT_NOT_FOUND);
        }

        GameProject gameProject = gameProjectMapper.selectById(artifact.getProjectId());
        if (gameProject == null || !gameProject.getUserId().equals(userId)) {
            log.warn("[产物] 查询产物详情失败：无权访问该产物 userId={} artifactUuid={} projectId={}",
                    userId, artifactUuid, artifact.getProjectId());
            throw new BusinessException(ErrorCode.FORBIDDEN_ARTIFACT_ACCESS);
        }

        log.info("[产物] 查询产物详情成功 userId={} artifactUuid={} projectId={}",
                userId, artifactUuid, artifact.getProjectId());
        return toVO(artifact);
    }

    private AgentArtifactVO toVO(AgentArtifact artifact) {
        return AgentArtifactVO.builder()
                .id(artifact.getId())
                .artifactUuid(artifact.getArtifactUuid())
                .projectId(artifact.getProjectId())
                .agentRunId(artifact.getAgentRunId())
                .artifactType(artifact.getArtifactType())
                .title(artifact.getTitle())
                .content(artifact.getContent())
                .createdAt(artifact.getCreatedAt())
                .updatedAt(artifact.getUpdatedAt())
                .build();
    }
}
