package com.example.gameworkbench.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.gameProject.GameProjectRequest;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.service.GameProjectService;
import com.example.gameworkbench.vo.project.GameProjectVO;
import com.example.gameworkbench.vo.project.ProjectRunSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameProjectServiceImpl implements GameProjectService {

    private final GameProjectMapper gameProjectMapper;

    @Override
    public GameProjectVO createProject(Long userId, GameProjectRequest request) {
        if (userId == null) {
            log.warn("[Project] create project rejected: unauthorized");
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        LocalDateTime now = LocalDateTime.now();

        GameProject gameProject = new GameProject();
        gameProject.setProjectUuid(UUID.randomUUID().toString());
        gameProject.setUserId(userId);
        gameProject.setName(request.getName());
        gameProject.setGameType(request.getGameType());
        gameProject.setTargetPlatform(request.getTargetPlatform());
        gameProject.setDescription(request.getDescription());
        gameProject.setStatus("ACTIVE");
        gameProject.setCreatedAt(now);
        gameProject.setUpdatedAt(now);

        log.info("[Project] create project started userId={} projectUuid={} name={}",
                userId, gameProject.getProjectUuid(), request.getName());
        gameProjectMapper.insert(gameProject);
        log.info("[Project] create project succeeded userId={} projectUuid={} projectId={}",
                userId, gameProject.getProjectUuid(), gameProject.getId());
        return toVo(gameProject);
    }

    @Override
    public List<GameProjectVO> listProjects(Long userId) {
        if (userId == null) {
            log.warn("[Project] list projects rejected: unauthorized");
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        log.info("[Project] list projects started userId={}", userId);
        List<GameProject> projects = gameProjectMapper.selectList(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getUserId, userId)
                .orderByDesc(GameProject::getCreatedAt));
        log.info("[Project] list projects succeeded userId={} count={}", userId, projects.size());
        return projects.stream().map(this::toVo).toList();
    }

    @Override
    public GameProjectVO getProject(Long userId, String projectUuid) {
        if (userId == null) {
            log.warn("[Project] get project rejected: unauthorized projectUuid={}", projectUuid);
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        log.info("[Project] get project started userId={} projectUuid={}", userId, projectUuid);
        GameProject gameProject = findByProjectUuid(projectUuid);
        if (gameProject == null) {
            log.warn("[Project] get project rejected: project not found userId={} projectUuid={}", userId, projectUuid);
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        if (!gameProject.getUserId().equals(userId)) {
            log.warn("[Project] get project rejected: forbidden userId={} projectUuid={} ownerUserId={}",
                    userId, projectUuid, gameProject.getUserId());
            throw new BusinessException(ErrorCode.FORBIDDEN_PROJECT_ACCESS);
        }

        log.info("[Project] get project succeeded userId={} projectUuid={}", userId, projectUuid);
        return toVo(gameProject);
    }

    @Override
    public GameProjectVO updateProject(Long userId, String projectUuid, GameProjectRequest request) {
        if (userId == null) {
            log.warn("[Project] update project rejected: unauthorized projectUuid={}", projectUuid);
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        log.info("[Project] update project started userId={} projectUuid={}", userId, projectUuid);
        GameProject gameProject = findByProjectUuid(projectUuid);
        if (gameProject == null) {
            log.warn("[Project] update project rejected: project not found userId={} projectUuid={}", userId, projectUuid);
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        if (!gameProject.getUserId().equals(userId)) {
            log.warn("[Project] update project rejected: forbidden userId={} projectUuid={} ownerUserId={}",
                    userId, projectUuid, gameProject.getUserId());
            throw new BusinessException(ErrorCode.FORBIDDEN_PROJECT_UPDATE);
        }

        gameProject.setName(request.getName());
        gameProject.setGameType(request.getGameType());
        gameProject.setTargetPlatform(request.getTargetPlatform());
        gameProject.setDescription(request.getDescription());
        gameProject.setUpdatedAt(LocalDateTime.now());
        gameProjectMapper.updateById(gameProject);

        log.info("[Project] update project succeeded userId={} projectUuid={}", userId, projectUuid);
        return toVo(gameProject);
    }

    @Override
    public List<ProjectRunSummaryVO> selectProjectRunSummary(Long userId) {
        if (userId == null) {
            log.warn("[Project] select project run summary rejected: unauthorized");
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        log.info("[Project] select project run summary started userId={}", userId);
        List<ProjectRunSummaryVO> summaries = gameProjectMapper.selectProjectRunSummary(userId);
        log.info("[Project] select project run summary succeeded userId={} count={}", userId, summaries.size());
        return summaries;
    }

    private GameProject findByProjectUuid(String projectUuid) {
        return gameProjectMapper.selectOne(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getProjectUuid, projectUuid));
    }

    private GameProjectVO toVo(GameProject gameProject) {
        return GameProjectVO.builder()
                .id(gameProject.getId())
                .projectUuid(gameProject.getProjectUuid())
                .userId(gameProject.getUserId())
                .name(gameProject.getName())
                .gameType(gameProject.getGameType())
                .targetPlatform(gameProject.getTargetPlatform())
                .description(gameProject.getDescription())
                .status(gameProject.getStatus())
                .createdAt(gameProject.getCreatedAt())
                .updatedAt(gameProject.getUpdatedAt())
                .build();
    }
}
