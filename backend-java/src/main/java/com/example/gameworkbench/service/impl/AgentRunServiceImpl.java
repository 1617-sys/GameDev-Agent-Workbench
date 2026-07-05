package com.example.gameworkbench.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.gameworkbench.entity.PromptTemplate;
import com.example.gameworkbench.mapper.PromptTemplateMapper;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.gameworkbench.client.PythonAgentClient;
import com.example.gameworkbench.client.dto.PythonAgentRequest;
import com.example.gameworkbench.client.dto.PythonAgentResponse;
import com.example.gameworkbench.common.enums.AgentRunStatus;
import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.agent.AgentRunRequest;
import com.example.gameworkbench.entity.AgentRun;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.mapper.AgentRunMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.service.AgentRunService;
import com.example.gameworkbench.vo.agent.AgentRunVO;
import com.example.gameworkbench.vo.project.AgentRunTypeSummaryVO;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunServiceImpl implements AgentRunService {

    private final AgentRunMapper agentRunMapper;
    private final GameProjectMapper gameProjectMapper;
    private final PythonAgentClient pythonAgentClient;
    private final ObjectMapper objectMapper;
    private final PromptTemplateMapper promptTemplateMapper;

    /**
     * 执行一次 Agent 运行任务。
     * <p>
     * 流程包括：校验用户权限与项目归属 → 创建运行记录 → 查询匹配的激活提示词模板 →
     * 构建请求并调用 Python Agent 服务 → 更新运行结果。
     * 无论成功或失败，都会将最终状态写回运行记录。
     *
     * @param userId  当前操作用户的 ID，不能为 {@code null}
     * @param request 包含项目 UUID、Agent 类型、标题、内容及上下文等参数的运行请求
     * @return 本次运行的结果 VO，包含运行 UUID、状态、耗时、输入/输出内容等信息
     */
    @Override
    public AgentRunVO run(Long userId, AgentRunRequest request) {
        /*
         * 校验用户身份：未登录或 userId 为空则直接拒绝。
         */
        if (userId == null) {
            log.warn("[Agent] run rejected: unauthorized agentType={}", request.getAgentType());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        /*
         * 校验项目归属：只有项目创建者才能对该项目发起 Agent 运行。
         */
        GameProject gameProject = gameProjectMapper.selectOne(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getProjectUuid, request.getProjectUuid())
                .eq(GameProject::getUserId, userId));
        if (gameProject == null) {
            log.warn("[Agent] run rejected: project not found or forbidden userId={} projectUuid={}",
                    userId, request.getProjectUuid());
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        /*
         * 初始化运行记录并持久化，状态置为 RUNNING，便于追踪与恢复。
         */
        long startTime = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        AgentRun agentRun = new AgentRun();
        agentRun.setRunUuid(UUID.randomUUID().toString());
        agentRun.setUserId(userId);
        agentRun.setProjectId(gameProject.getId());
        agentRun.setProjectUuid(gameProject.getProjectUuid());
        agentRun.setAgentType(request.getAgentType().name());
        agentRun.setInputContent(writeJsonSafely(request));
        agentRun.setStatus(AgentRunStatus.RUNNING.name());
        agentRun.setCreatedAt(now);
        agentRun.setUpdatedAt(now);
        agentRunMapper.insert(agentRun);

        log.info("[Agent] run started userId={} projectId={} projectUuid={} runUuid={} agentType={}",
                userId, agentRun.getProjectId(), agentRun.getProjectUuid(), agentRun.getRunUuid(),
                request.getAgentType());

        try {

            /*
             * 查询当前 Agent 类型对应的最新激活版提示词模板。
             */
            PromptTemplate promptTemplate = promptTemplateMapper.selectOne(
                    new LambdaQueryWrapper<PromptTemplate>()
                            .eq(PromptTemplate::getAgentType, request.getAgentType().name())
                            .eq(PromptTemplate::getStatus, "ACTIVE")
                            .orderByDesc(PromptTemplate::getVersion)
                            .last("LIMIT 1")
            );
            if (promptTemplate == null) {
                log.warn("[Agent] active prompt template missing userId={} projectUuid={} runUuid={} agentType={}",
                        userId, agentRun.getProjectUuid(), agentRun.getRunUuid(), request.getAgentType());
                throw new BusinessException(ErrorCode.ACTIVE_PROMPT_TEMPLATE_NOT_FOUND);
            }

            log.info("[Agent] prompt template selected userId={} runUuid={} agentType={} templateUuid={} version={}",
                    userId, agentRun.getRunUuid(), request.getAgentType(),
                    promptTemplate.getTemplateUuid(), promptTemplate.getVersion());

            /*
             * 组装请求参数，将前端输入与后端查询到的提示词模板合并，通过 Python 客户端调用 Agent 服务。
             */
            PythonAgentRequest pythonRequest = PythonAgentRequest.builder()
                    .projectUuid(request.getProjectUuid())
                    .title(request.getTitle())
                    .content(request.getContent())
                    .context(request.getContext())
                    .systemPrompt(promptTemplate.getSystemPrompt())
                    .userPromptTemplate(promptTemplate.getUserPromptTemplate())
                    .templateUuid(promptTemplate.getTemplateUuid())
                    .templateVersion(promptTemplate.getVersion())
                    .userId(userId)
                    .build();

            PythonAgentResponse pythonResponse = pythonAgentClient.invoke(request.getAgentType(), pythonRequest);
            /*
             * 将 Python 服务返回结果序列化为 JSON 字符串，写入运行记录并标记成功。
             */
            String outputContent = pythonResponse.getData() == null
                    ? null
                    : objectMapper.writeValueAsString(pythonResponse.getData());

            agentRun.setOutputContent(outputContent);
            agentRun.setErrorMessage(null);
            agentRun.setStatus(AgentRunStatus.SUCCESS.name());
            agentRun.setTimeTakenMs(System.currentTimeMillis() - startTime);
            agentRun.setUpdatedAt(LocalDateTime.now());
            agentRunMapper.updateById(agentRun);

            log.info("[Agent] run succeeded userId={} projectId={} projectUuid={} runUuid={} agentType={} timeTakenMs={}",
                    userId, agentRun.getProjectId(), agentRun.getProjectUuid(), agentRun.getRunUuid(),
                    request.getAgentType(), agentRun.getTimeTakenMs());
            return toVO(agentRun);
        } catch (BusinessException exception) {
            /*
             * 业务异常处理：记录失败信息并重新抛出，由上层统一处理。
             */
            agentRun.setStatus(AgentRunStatus.FAILED.name());
            agentRun.setErrorMessage(exception.getMessage());
            agentRun.setTimeTakenMs(System.currentTimeMillis() - startTime);
            agentRun.setUpdatedAt(LocalDateTime.now());
            agentRunMapper.updateById(agentRun);

            log.warn("[Agent] run failed userId={} projectId={} projectUuid={} runUuid={} agentType={} timeTakenMs={} message={}",
                    userId, agentRun.getProjectId(), agentRun.getProjectUuid(), agentRun.getRunUuid(),
                    request.getAgentType(), agentRun.getTimeTakenMs(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            /*
             * 未知异常兜底：记录错误日志并包装为业务异常抛出，避免暴露内部细节。
             */
            agentRun.setStatus(AgentRunStatus.FAILED.name());
            agentRun.setErrorMessage(ErrorCode.AGENT_RUN_ERROR.getMessage());
            agentRun.setTimeTakenMs(System.currentTimeMillis() - startTime);
            agentRun.setUpdatedAt(LocalDateTime.now());
            agentRunMapper.updateById(agentRun);

            log.error("[Agent] run exception userId={} projectId={} projectUuid={} runUuid={} agentType={} timeTakenMs={}",
                    userId, agentRun.getProjectId(), agentRun.getProjectUuid(), agentRun.getRunUuid(),
                    request.getAgentType(), agentRun.getTimeTakenMs(), exception);
            throw new BusinessException(ErrorCode.AGENT_RUN_ERROR);
        }
    }

    @Override
    public Page<AgentRunVO> listRuns(
            Long userId,
            Integer pageNum,
            Integer pageSize,
            String projectUuid,
            AgentType agentType,
            AgentRunStatus status
    ) {
        if (userId == null) {
            log.warn("[Agent] list runs rejected: unauthorized");
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Long projectId = null;
        if (projectUuid != null && !projectUuid.isBlank()) {
            GameProject gameProject = gameProjectMapper.selectOne(new LambdaQueryWrapper<GameProject>()
                    .eq(GameProject::getProjectUuid, projectUuid)
                    .eq(GameProject::getUserId, userId));
            if (gameProject == null) {
                log.warn("[Agent] list runs rejected: project not found or forbidden userId={} projectUuid={}",
                        userId, projectUuid);
                throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
            }
            projectId = gameProject.getId();
        }

        Page<AgentRun> page = agentRunMapper.selectPage(
                new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize)),
                new LambdaQueryWrapper<AgentRun>()
                        .eq(AgentRun::getUserId, userId)
                        .eq(projectId != null, AgentRun::getProjectId, projectId)
                        .eq(agentType != null, AgentRun::getAgentType, agentType == null ? null : agentType.name())
                        .eq(status != null, AgentRun::getStatus, status == null ? null : status.name())
                        .orderByDesc(AgentRun::getCreatedAt)
        );

        Page<AgentRunVO> pageVO = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        pageVO.setRecords(page.getRecords().stream().map(this::toVO).toList());

        log.info("[AgentRun] list runs succeeded userId={} projectUuid={} agentType={} status={} pageNum={} pageSize={} total={}",
                userId, projectUuid, agentType, status, page.getCurrent(), page.getSize(), page.getTotal());
        return pageVO;
    }

    @Override
    public AgentRunVO getRun(Long userId, String runUuid) {
        if (userId == null) {
            log.warn("[Agent] get run rejected: unauthorized runUuid={}", runUuid);
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        log.info("[Agent] get run started userId={} runUuid={}", userId, runUuid);
        AgentRun agentRun = agentRunMapper.selectOne(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getRunUuid, runUuid)
                .eq(AgentRun::getUserId, userId));
        if (agentRun == null) {
            log.warn("[Agent] get run rejected: run not found userId={} runUuid={}", userId, runUuid);
            throw new BusinessException(ErrorCode.AGENT_RUN_NOT_FOUND);
        }

        log.info("[Agent] get run succeeded userId={} runUuid={} status={}",
                userId, runUuid, agentRun.getStatus());
        return toVO(agentRun);
    }

    @Override
    public List<AgentRunTypeSummaryVO> selectAgentRunTypeSummary(Long userId) {
        if (userId == null) {
            log.warn("[Agent] select agent run type summary rejected: unauthorized");
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        List<AgentRun> runs = agentRunMapper.selectList(
                new LambdaQueryWrapper<AgentRun>()
                        .eq(AgentRun::getUserId, userId)
        );

        Map<String, List<AgentRun>> groupedByType = runs.stream()
                .collect(Collectors.groupingBy(AgentRun::getAgentType));

        List<AgentRunTypeSummaryVO> result = groupedByType.entrySet().stream()
                .map(entry -> {
                    List<AgentRun> typeRuns = entry.getValue();
                    long totalCount = typeRuns.size();
                    long successCount = typeRuns.stream()
                            .filter(r -> AgentRunStatus.SUCCESS.name().equals(r.getStatus()))
                            .count();
                    long failedCount = typeRuns.stream()
                            .filter(r -> AgentRunStatus.FAILED.name().equals(r.getStatus()))
                            .count();
                    double avgTimeTakenMs = typeRuns.stream()
                            .filter(r -> r.getTimeTakenMs() != null)
                            .mapToLong(AgentRun::getTimeTakenMs)
                            .average()
                            .orElse(0.0);

                    return AgentRunTypeSummaryVO.builder()
                            .agentType(entry.getKey())
                            .totalCount(totalCount)
                            .successCount(successCount)
                            .failedCount(failedCount)
                            .avgTimeTakenMs(avgTimeTakenMs)
                            .build();
                })
                .collect(Collectors.toList());

        log.info("[Agent] select agent run type summary succeeded userId={} resultSize={}",
                userId, result.size());
        return result;
    }

    private AgentRunVO toVO(AgentRun agentRun) {
        return AgentRunVO.builder()
                .id(agentRun.getId())
                .runUuid(agentRun.getRunUuid())
                .userId(agentRun.getUserId())
                .projectId(agentRun.getProjectId())
                .projectUuid(agentRun.getProjectUuid())
                .agentType(agentRun.getAgentType())
                .inputContent(agentRun.getInputContent())
                .outputContent(agentRun.getOutputContent())
                .status(agentRun.getStatus())
                .errorMessage(agentRun.getErrorMessage())
                .timeTakenMs(agentRun.getTimeTakenMs())
                .createdAt(agentRun.getCreatedAt())
                .updatedAt(agentRun.getUpdatedAt())
                .build();
    }

    private String writeJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            log.warn("[Agent] input serialization failed, fallback to String.valueOf", exception);
            return String.valueOf(value);
        }
    }

    private long normalizePageNum(Integer pageNum) {
        if (pageNum == null || pageNum < 1) {
            return 1L;
        }
        return pageNum.longValue();
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10L;
        }
        return Math.min(pageSize, 100);
    }
}
