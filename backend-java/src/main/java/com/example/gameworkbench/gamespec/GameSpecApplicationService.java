package com.example.gameworkbench.gamespec;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;

/**
 * GameSpec 编译器的应用层门面。
 *
 * <p>{@link GameSpecCompiler} 本身是纯领域编译器，不认识用户和项目；本服务在调用编译器前
 * 统一补上身份与项目归属检查，供 HTTP 编译和 AI 修复流程复用。</p>
 */
@Service
@RequiredArgsConstructor
public class GameSpecApplicationService {
    private final GameProjectMapper projects;
    private final GameSpecCompiler compiler;
    private final ArcadeCollectCapabilityRegistry capabilities;

    public ObjectNode capabilities() {
        return capabilities.snapshot();
    }

    @Transactional(readOnly = true)
    public GameSpecCompilationResult compile(Long userId, String projectUuid, JsonNode spec) {
        // 权限检查必须发生在 AI 调用或构建之前，既保护数据，也避免未授权请求消耗外部资源。
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        GameProject project = projects.selectOne(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getProjectUuid, projectUuid));
        if (project == null) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        if (!Objects.equals(project.getUserId(), userId)) throw new BusinessException(ErrorCode.FORBIDDEN_PROJECT_ACCESS);
        return compiler.compile(spec);
    }
}
