package com.example.gameworkbench.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.AgentRunStatus;
import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.agent.AgentRunRequest;
import com.example.gameworkbench.dto.workflow.WorkflowRunRequest;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.service.AgentRunService;
import com.example.gameworkbench.service.WorkflowService;
import com.example.gameworkbench.vo.agent.AgentRunVO;
import com.example.gameworkbench.vo.workflow.WorkflowRunVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private static final String GAME_DESIGN_WORKFLOW = "GAME_DESIGN";

    private final GameProjectMapper gameProjectMapper;
    private final WorkflowRunMapper workflowRunMapper;
    private final AgentArtifactMapper agentArtifactMapper;
    private final AgentRunService agentRunService;

    @Override
    public WorkflowRunVO run(Long userId, WorkflowRunRequest request) {
        return createWorkflowRun(userId, request);
    }

    @Override
    public WorkflowRunVO getWorkflowRun(Long userId, String workflowRunUuid) {
        if (userId == null) {
            log.warn("[Workflow] 查询失败：未登录请求 workflowRunUuid={}", workflowRunUuid);
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        WorkflowRun workflowRun = workflowRunMapper.selectOne(new LambdaQueryWrapper<WorkflowRun>()
                .eq(WorkflowRun::getWorkflowRunUuid, workflowRunUuid)
                .eq(WorkflowRun::getUserId, userId));
        if (workflowRun == null) {
            log.warn("[Workflow] 查询失败：工作流记录不存在 userId={} workflowRunUuid={}",
                    userId, workflowRunUuid);
            throw new BusinessException(ErrorCode.WORKFLOW_RUN_NOT_FOUND);
        }

        GameProject gameProject = gameProjectMapper.selectById(workflowRun.getProjectId());
        String projectUuid = gameProject == null ? null : gameProject.getProjectUuid();

        log.info("[Workflow] 查询成功 userId={} workflowRunUuid={} status={}",
                userId, workflowRunUuid, workflowRun.getStatus());
        return toVO(workflowRun, projectUuid, List.of());
    }

    public WorkflowRunVO createWorkflowRun(Long userId, WorkflowRunRequest request) {
        if (userId == null) {
            log.warn("[Workflow] 创建失败：未登录请求 projectUuid={}", request.getProjectUuid());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        GameProject gameProject = getUserProject(userId, request.getProjectUuid());
        long startTime = System.currentTimeMillis();

        WorkflowRun workflowRun = createRunningWorkflowRun(userId, request, gameProject);
        workflowRunMapper.insert(workflowRun);

        log.info("[Workflow] 执行开始 userId={} projectId={} workflowRunUuid={}",
                userId, gameProject.getId(), workflowRun.getWorkflowRunUuid());

        try {
            WorkflowRunVO.WorkflowStepVO gameConceptStep = runGameConceptStep(userId, request);
            WorkflowRunVO.WorkflowStepVO coreLoopDesignStep =
                    runCoreLoopDesignStep(userId, request, gameConceptStep);
            WorkflowRunVO.WorkflowStepVO taskBreakdownStep =
                    runTaskBreakdownStep(userId, request, gameConceptStep, coreLoopDesignStep);

            List<WorkflowRunVO.WorkflowStepVO> steps =
                    List.of(gameConceptStep, coreLoopDesignStep, taskBreakdownStep);

            markWorkflowSuccess(
                    workflowRun,
                    startTime,
                    buildSummary(gameConceptStep, coreLoopDesignStep, taskBreakdownStep)
            );

            log.info("[Workflow] 执行成功 userId={} projectId={} workflowRunUuid={} timeTakenMs={}",
                    userId, gameProject.getId(), workflowRun.getWorkflowRunUuid(), workflowRun.getTimeTakenMs());

            return toVO(workflowRun, gameProject.getProjectUuid(), steps);
        } catch (BusinessException exception) {
            markWorkflowFailed(workflowRun, startTime, exception.getMessage());
            log.warn("[Workflow] 执行失败 userId={} projectId={} workflowRunUuid={} message={}",
                    userId, gameProject.getId(), workflowRun.getWorkflowRunUuid(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            markWorkflowFailed(workflowRun, startTime, ErrorCode.SYSTEM_ERROR.getMessage());
            log.error("[Workflow] 执行异常 userId={} projectId={} workflowRunUuid={}",
                    userId, gameProject.getId(), workflowRun.getWorkflowRunUuid(), exception);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    private WorkflowRun createRunningWorkflowRun(
            Long userId,
            WorkflowRunRequest request,
            GameProject gameProject
    ) {
        LocalDateTime now = LocalDateTime.now();
        return WorkflowRun.builder()
                .workflowRunUuid(UUID.randomUUID().toString())
                .projectId(gameProject.getId())
                .userId(userId)
                .status(AgentRunStatus.RUNNING.name())
                .workflowType(GAME_DESIGN_WORKFLOW)
                .inputContent(request.getIdea())
                .timeTakenMs(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private void markWorkflowSuccess(WorkflowRun workflowRun, long startTime, String summary) {
        workflowRun.setSummary(summary);
        workflowRun.setStatus(AgentRunStatus.SUCCESS.name());
        workflowRun.setErrorMessage(null);
        workflowRun.setTimeTakenMs(System.currentTimeMillis() - startTime);
        workflowRun.setUpdatedAt(LocalDateTime.now());
        workflowRunMapper.updateById(workflowRun);
    }

    private void markWorkflowFailed(WorkflowRun workflowRun, long startTime, String errorMessage) {
        workflowRun.setStatus(AgentRunStatus.FAILED.name());
        workflowRun.setErrorMessage(errorMessage);
        workflowRun.setTimeTakenMs(System.currentTimeMillis() - startTime);
        workflowRun.setUpdatedAt(LocalDateTime.now());
        workflowRunMapper.updateById(workflowRun);
    }

    private WorkflowRunVO.WorkflowStepVO runGameConceptStep(Long userId, WorkflowRunRequest request) {
        return runWorkflowStep(
                1,
                userId,
                request,
                AgentType.GAME_CONCEPT,
                request.getIdea(),
                request.getContext()
        );
    }

    private WorkflowRunVO.WorkflowStepVO runCoreLoopDesignStep(
            Long userId,
            WorkflowRunRequest request,
            WorkflowRunVO.WorkflowStepVO gameConceptStep
    ) {
        return runWorkflowStep(
                2,
                userId,
                request,
                AgentType.CORE_LOOP_DESIGN,
                request.getIdea(),
                buildStepContext(request.getContext(), gameConceptStep)
        );
    }

    private WorkflowRunVO.WorkflowStepVO runTaskBreakdownStep(
            Long userId,
            WorkflowRunRequest request,
            WorkflowRunVO.WorkflowStepVO gameConceptStep,
            WorkflowRunVO.WorkflowStepVO coreLoopStep
    ) {
        return runWorkflowStep(
                3,
                userId,
                request,
                AgentType.TASK_BREAKDOWN,
                request.getIdea(),
                buildStepContext(request.getContext(), gameConceptStep, coreLoopStep)
        );
    }

    private WorkflowRunVO.WorkflowStepVO runWorkflowStep(
            Integer stepOrder,
            Long userId,
            WorkflowRunRequest workflowRequest,
            AgentType agentType,
            String content,
            String context
    ) {
        AgentRunVO agentRun = agentRunService.run(userId, AgentRunRequest.builder()
                .projectUuid(workflowRequest.getProjectUuid())
                .agentType(agentType)
                .title(workflowRequest.getTitle())
                .content(content)
                .context(context)
                .build());

        AgentArtifact artifact = createArtifact(agentRun, workflowRequest.getTitle(), agentType);

        log.info("[Workflow] 步骤完成 stepOrder={} agentType={} agentRunUuid={} artifactUuid={}",
                stepOrder, agentType, agentRun.getRunUuid(), artifact.getArtifactUuid());

        return WorkflowRunVO.WorkflowStepVO.builder()
                .stepOrder(stepOrder)
                .agentType(agentType.name())
                .artifactType(agentType.getArtifactType().name())
                .title(workflowRequest.getTitle())
                .content(agentRun.getOutputContent())
                .agentRunUuid(agentRun.getRunUuid())
                .artifactUuid(artifact.getArtifactUuid())
                .build();
    }

    private AgentArtifact createArtifact(AgentRunVO agentRun, String title, AgentType agentType) {
        LocalDateTime now = LocalDateTime.now();

        AgentArtifact agentArtifact = AgentArtifact.builder()
                .artifactUuid(UUID.randomUUID().toString())
                .projectId(agentRun.getProjectId())
                .agentRunId(agentRun.getId())
                .artifactType(agentType.getArtifactType().name())
                .title(title)
                .content(agentRun.getOutputContent())
                .createdAt(now)
                .updatedAt(now)
                .build();
        agentArtifactMapper.insert(agentArtifact);
        return agentArtifact;
    }

    private String buildStepContext(String baseContext, WorkflowRunVO.WorkflowStepVO... previousSteps) {
        StringBuilder builder = new StringBuilder();
        if (baseContext != null && !baseContext.isBlank()) {
            builder.append(baseContext).append("\n\n");
        }
        for (WorkflowRunVO.WorkflowStepVO step : previousSteps) {
            builder.append("上一阶段 ")
                    .append(step.getAgentType())
                    .append(" 输出：\n")
                    .append(step.getContent())
                    .append("\n\n");
        }
        return builder.toString();
    }

    private String buildSummary(WorkflowRunVO.WorkflowStepVO... steps) {
        return "已完成游戏设计工作流，共生成 " + steps.length + " 个产物";
    }

    private WorkflowRunVO toVO(
            WorkflowRun workflowRun,
            String projectUuid,
            List<WorkflowRunVO.WorkflowStepVO> steps
    ) {
        return WorkflowRunVO.builder()
                .id(workflowRun.getId())
                .workflowRunUuid(workflowRun.getWorkflowRunUuid())
                .projectId(workflowRun.getProjectId())
                .projectUuid(projectUuid)
                .userId(workflowRun.getUserId())
                .workflowType(workflowRun.getWorkflowType())
                .status(workflowRun.getStatus())
                .inputContent(workflowRun.getInputContent())
                .summary(workflowRun.getSummary())
                .errorMessage(workflowRun.getErrorMessage())
                .timeTakenMs(workflowRun.getTimeTakenMs())
                .steps(steps)
                .createdAt(workflowRun.getCreatedAt())
                .updatedAt(workflowRun.getUpdatedAt())
                .build();
    }

    private GameProject getUserProject(Long userId, String projectUuid) {
        GameProject gameProject = gameProjectMapper.selectOne(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getProjectUuid, projectUuid)
                .eq(GameProject::getUserId, userId));
        if (gameProject == null) {
            log.warn("[Workflow] 项目不存在或无权访问 userId={} projectUuid={}", userId, projectUuid);
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return gameProject;
    }
}
