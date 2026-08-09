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
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        GameProject project = projects.selectOne(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getProjectUuid, projectUuid));
        if (project == null) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        if (!Objects.equals(project.getUserId(), userId)) throw new BusinessException(ErrorCode.FORBIDDEN_PROJECT_ACCESS);
        return compiler.compile(spec);
    }
}
