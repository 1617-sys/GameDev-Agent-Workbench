package com.example.gameworkbench.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.gameProject.GameProjectRequest;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.service.GameProjectService;
import com.example.gameworkbench.vo.project.GameProjectVO;
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
            log.warn("[项目] 创建项目失败：未登录请求");
            throw new BusinessException(40101, "请先登录");
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

        log.info("[项目] 创建项目开始 userId={} projectUuid={} name={}",
                userId, gameProject.getProjectUuid(), request.getName());
        gameProjectMapper.insert(gameProject);
        log.info("[项目] 创建项目成功 userId={} projectUuid={} projectId={}",
                userId, gameProject.getProjectUuid(), gameProject.getId());
        return toVo(gameProject);
    }

    @Override
    public List<GameProjectVO> listProjects(Long userId) {
        if (userId == null) {
            log.warn("[项目] 查询项目列表失败：未登录请求");
            throw new BusinessException(40101, "请先登录");
        }

        log.info("[项目] 查询项目列表开始 userId={}", userId);
        List<GameProject> projects = gameProjectMapper.selectList(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getUserId, userId)
                .orderByDesc(GameProject::getCreatedAt));
        log.info("[项目] 查询项目列表成功 userId={} count={}", userId, projects.size());
        return projects.stream().map(this::toVo).toList();
    }

    @Override
    public GameProjectVO getProject(Long userId, String projectUuid) {
        if (userId == null) {
            log.warn("[项目] 查询项目失败：未登录请求 projectUuid={}", projectUuid);
            throw new BusinessException(40101, "请先登录");
        }

        log.info("[项目] 查询项目开始 userId={} projectUuid={}", userId, projectUuid);
        GameProject gameProject = findByProjectUuid(projectUuid);
        if (gameProject == null) {
            log.warn("[项目] 查询项目失败：项目不存在 userId={} projectUuid={}", userId, projectUuid);
            throw new BusinessException(40401, "项目不存在");
        }
        if (!gameProject.getUserId().equals(userId)) {
            log.warn("[项目] 查询项目失败：无权访问该项目 userId={} projectUuid={} ownerUserId={}",
                    userId, projectUuid, gameProject.getUserId());
            throw new BusinessException(40301, "无权访问该项目");
        }

        log.info("[项目] 查询项目成功 userId={} projectUuid={}", userId, projectUuid);
        return toVo(gameProject);
    }

    @Override
    public GameProjectVO updateProject(Long userId, String projectUuid, GameProjectRequest request) {
        if (userId == null) {
            log.warn("[项目] 更新项目失败：未登录请求 projectUuid={}", projectUuid);
            throw new BusinessException(40101, "请先登录");
        }

        log.info("[项目] 更新项目开始 userId={} projectUuid={}", userId, projectUuid);
        GameProject gameProject = findByProjectUuid(projectUuid);
        if (gameProject == null) {
            log.warn("[项目] 更新项目失败：项目不存在 userId={} projectUuid={}", userId, projectUuid);
            throw new BusinessException(40401, "项目不存在");
        }
        if (!gameProject.getUserId().equals(userId)) {
            log.warn("[项目] 更新项目失败：无权更新该项目 userId={} projectUuid={} ownerUserId={}",
                    userId, projectUuid, gameProject.getUserId());
            throw new BusinessException(40301, "无权更新该项目");
        }

        gameProject.setName(request.getName());
        gameProject.setGameType(request.getGameType());
        gameProject.setTargetPlatform(request.getTargetPlatform());
        gameProject.setDescription(request.getDescription());
        gameProject.setUpdatedAt(LocalDateTime.now());
        gameProjectMapper.updateById(gameProject);

        log.info("[项目] 更新项目成功 userId={} projectUuid={}", userId, projectUuid);
        return toVo(gameProject);
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
