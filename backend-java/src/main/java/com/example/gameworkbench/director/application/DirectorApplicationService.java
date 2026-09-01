package com.example.gameworkbench.director.application;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.director.persistence.CreateDirectorRunCommand;
import com.example.gameworkbench.director.persistence.DirectorRunService;
import com.example.gameworkbench.director.persistence.DirectorRunView;
import com.example.gameworkbench.dto.director.SubmitDirectorRunRequest;
import com.example.gameworkbench.entity.DirectorRun;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.mapper.DirectorRunMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Director 运行的应用层入口，负责把 HTTP 请求转换成持久化状态机操作。
 *
 * <p>本类只做用例编排：验证项目归属、幂等创建 Run、推进初始状态并发布启动事件。
 * Director 的循环决策和工具调用由异步 Worker 完成，避免提交接口被长时间阻塞。</p>
 */
@Service
@RequiredArgsConstructor
public class DirectorApplicationService {
    private final GameProjectMapper projects;
    private final DirectorRunService runs;
    private final DirectorRunMapper runRows;
    private final ApplicationEventPublisher events;

    /**
     * 创建或重放一个 Director Run。
     *
     * <p>只有新建的 PENDING Run 才会被推进并发布事件；幂等重放拿到非 PENDING Run 时
     * 直接返回，防止同一个请求重复启动两个执行循环。</p>
     */
    @Transactional
    public DirectorRun submit(long userId, String projectUuid, String key, String traceId,
            SubmitDirectorRunRequest request) {
        GameProject project = owned(userId, projectUuid);
        DirectorRun run = runs.create(userId, project.getId(),
                new CreateDirectorRunCommand(key, request.getGoal(), request.getBudget(), request.getFacts()));

        if ("PENDING".equals(run.getStatus())) {
            // 外部 traceId 只有满足安全字符和长度约束时才沿用，否则生成服务端 traceId。
            String trace = traceId != null && traceId.matches("[A-Za-z0-9._:-]{8,64}")
                    ? traceId : UUID.randomUUID().toString();
            run = runs.transition(userId, project.getId(), run.getRunUuid(), run.getStateVersion(),
                    "RUNNING", run.getCheckpointJson(), null, null);
            runRows.setTrace(run.getRunUuid(), trace);
            run.setTraceId(trace);

            // 事件监听器在事务提交后唤醒 Worker，避免 Worker 读到尚未提交的 RUNNING 状态。
            events.publishEvent(new DirectorRunRequested(run.getRunUuid()));
        }
        return run;
    }

    /** 查询 Run 及其决策、工具调用等聚合视图。 */
    public DirectorRunView get(long userId, String projectUuid, String runUuid) {
        GameProject project = owned(userId, projectUuid);
        return runs.get(userId, project.getId(), runUuid);
    }

    /** 使用 expectedVersion 取消 Run；旧页面提交的过期版本会被状态机拒绝。 */
    @Transactional
    public DirectorRun cancel(long userId, String projectUuid, String runUuid, long expectedVersion) {
        GameProject project = owned(userId, projectUuid);
        DirectorRunView view = runs.get(userId, project.getId(), runUuid);
        return runs.transition(userId, project.getId(), runUuid, expectedVersion, "CANCELED",
                view.run().getCheckpointJson(), null, "USER_CANCELED");
    }

    /** 所有公开用例首先经过该查询，防止跨用户读取或操纵项目。 */
    private GameProject owned(long userId, String uuid) {
        GameProject project = projects.selectOne(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getProjectUuid, uuid)
                .eq(GameProject::getUserId, userId));
        if (project == null) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        return project;
    }
}
