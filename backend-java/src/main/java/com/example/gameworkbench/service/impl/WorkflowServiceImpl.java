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
            log.warn("[Workflow] get workflow rejected: unauthorized workflowRunUuid={}", workflowRunUuid);
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        WorkflowRun workflowRun = workflowRunMapper.selectOne(new LambdaQueryWrapper<WorkflowRun>()
                .eq(WorkflowRun::getWorkflowRunUuid, workflowRunUuid)
                .eq(WorkflowRun::getUserId, userId));
        if (workflowRun == null) {
            log.warn("[Workflow] get workflow rejected: workflow run not found userId={} workflowRunUuid={}",
                    userId, workflowRunUuid);
            throw new BusinessException(ErrorCode.WORKFLOW_RUN_NOT_FOUND);
        }

        GameProject gameProject = gameProjectMapper.selectById(workflowRun.getProjectId());
        String projectUuid = gameProject == null ? null : gameProject.getProjectUuid();

        log.info("[Workflow] get workflow succeeded userId={} workflowRunUuid={} status={}",
                userId, workflowRunUuid, workflowRun.getStatus());
        return toVO(workflowRun, projectUuid, List.of());
    }


    /**
     * 创建并执行一次工作流运行，按顺序串联三个Agent阶段：游戏概念生成 → 核心玩法设计 → 任务拆解。
     * <p>
     * 流程：校验用户权限 → 获取项目 → 创建RUNNING状态的工作流记录 →
     * 依次执行三个步骤（后一步依赖前一步的输出作为上下文） → 标记成功并返回完整VO。
     * 任意步骤抛出的 {@link BusinessException} 直接向上传播；未知异常统一包装为
     * {@code BusinessException(ErrorCode.SYSTEM_ERROR)} 抛出，确保上层能统一处理。
     *
     * @param userId  当前请求用户的唯一标识，为 {@code null} 时抛出 {@link BusinessException}
     * @param request 包含项目UUID、创意描述、上下文等信息的工作流请求参数
     * @return 包含工作流运行信息及三个步骤结果的视图对象
     * @throws BusinessException 用户未授权或工作流执行失败时抛出
     */
    public WorkflowRunVO createWorkflowRun(Long userId, WorkflowRunRequest request) {
        if (userId == null) {
            log.warn("[Workflow] create workflow rejected: unauthorized projectUuid={}", request.getProjectUuid());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    
        /*
         * 校验用户对项目的归属权限，获取项目信息并创建RUNNING状态的工作流记录写入数据库。
         */
        GameProject gameProject = getUserProject(userId, request.getProjectUuid());
        long startTime = System.currentTimeMillis();
    
        WorkflowRun workflowRun = createRunningWorkflowRun(userId, request, gameProject);
        workflowRunMapper.insert(workflowRun);
    
        log.info("[Workflow] run started userId={} projectId={} workflowRunUuid={}",
                userId, gameProject.getId(), workflowRun.getWorkflowRunUuid());
    
        /*
         * 按依赖顺序串联执行三个Agent阶段，后一步以前一步的输出作为上下文输入。
         */
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
    
            log.info("[Workflow] run succeeded userId={} projectId={} workflowRunUuid={} timeTakenMs={}",
                    userId, gameProject.getId(), workflowRun.getWorkflowRunUuid(), workflowRun.getTimeTakenMs());
    
            return toVO(workflowRun, gameProject.getProjectUuid(), steps);
        } catch (BusinessException exception) {
            /*
             * 业务异常：标记工作流失败，保留原始异常信息后继续向上传播。
             */
            markWorkflowFailed(workflowRun, startTime, exception.getMessage());
            log.warn("[Workflow] run failed userId={} projectId={} workflowRunUuid={} message={}",
                    userId, gameProject.getId(), workflowRun.getWorkflowRunUuid(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            /*
             * 未知异常：标记工作流失败，包装为BusinessException(SYSTEM_ERROR)再抛出，避免暴露内部细节。
             */
            markWorkflowFailed(workflowRun, startTime, ErrorCode.SYSTEM_ERROR.getMessage());
            log.error("[Workflow] run exception userId={} projectId={} workflowRunUuid={} exceptionType={}",
                    userId, gameProject.getId(), workflowRun.getWorkflowRunUuid(), exception.getClass().getName());
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

        log.info("[Workflow] step completed stepOrder={} agentType={} agentRunUuid={} artifactUuid={}",
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
            builder.append("Previous step ")
                    .append(step.getAgentType())
                    .append(" output:\n")
                    .append(step.getContent())
                    .append("\n\n");
        }
        return builder.toString();
    }

    private String buildSummary(WorkflowRunVO.WorkflowStepVO... steps) {
        return "Game design workflow completed. Generated " + steps.length + " artifacts.";
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
            log.warn("[Workflow] project not found or forbidden userId={} projectUuid={}", userId, projectUuid);
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return gameProject;
    }
}
