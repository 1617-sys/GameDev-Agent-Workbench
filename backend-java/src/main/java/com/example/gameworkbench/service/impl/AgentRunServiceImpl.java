package com.example.gameworkbench.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.gameworkbench.entity.PromptTemplate;
import com.example.gameworkbench.mapper.PromptTemplateMapper;
import com.example.gameworkbench.entity.ModelCallMetric;
import com.example.gameworkbench.entity.PromptVersion;
import com.example.gameworkbench.mapper.PromptVersionMapper;
import com.example.gameworkbench.service.ModelCallMetricService;
import com.example.gameworkbench.service.RetrievalService;
import com.example.gameworkbench.service.RetrievalRequest;
import com.example.gameworkbench.service.RetrievalRecordService;
import com.example.gameworkbench.service.KnowledgeStorage;
import com.example.gameworkbench.service.KnowledgeChunker;
import com.example.gameworkbench.service.EmbeddingProvider;
import com.example.gameworkbench.observability.ApplicationObservability;
import com.fasterxml.jackson.databind.JsonNode;
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
    private final PromptVersionMapper promptVersionMapper;
    private final ModelCallMetricService modelCallMetricService;
    private final RetrievalService retrievalService;
    private final RetrievalRecordService retrievalRecordService;
    private final KnowledgeStorage knowledgeStorage;
    private final EmbeddingProvider embeddingProvider;
    private final ApplicationObservability observability;

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
        agentRun.setMockState("UNKNOWN");
        agentRun.setRagEnabled(Boolean.TRUE.equals(request.getRagEnabled()));
        agentRun.setRagStatus(Boolean.TRUE.equals(request.getRagEnabled()) ? "PENDING" : "DISABLED");
        agentRun.setContextBudget(request.getRagContextBudget() == null ? 8000 : Math.min(request.getRagContextBudget(), 50000));
        agentRun.setRetrievalVersion("retrieval-v1");
        agentRun.setChunkingVersion(KnowledgeChunker.VERSION);
        agentRun.setEmbeddingModel(embeddingProvider.model());
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

            PromptVersion promptVersion = promptVersionMapper.selectActiveByAgentType(request.getAgentType().name());
            if (promptVersion == null) {
                throw new BusinessException(ErrorCode.ACTIVE_PROMPT_TEMPLATE_NOT_FOUND);
            }
            agentRun.setPromptVersionId(promptVersion.getId());
            agentRun.setRagExperimentKey(sha256(gameProject.getId() + "|" + request.getAgentType() + "|" + request.getTitle() + "|" + request.getContent() + "|" + (request.getContext() == null ? "" : request.getContext()) + "|" + promptVersion.getId()));
            agentRunMapper.updateById(agentRun);

            log.info("[Agent] prompt template selected userId={} runUuid={} agentType={} templateUuid={} version={}",
                    userId, agentRun.getRunUuid(), request.getAgentType(),
                    promptTemplate.getTemplateUuid(), promptTemplate.getVersion());

            /*
             * 组装请求参数，将前端输入与后端查询到的提示词模板合并，通过 Python 客户端调用 Agent 服务。
             */
            Object ragPayload = buildRagPayload(agentRun, request);
            PythonAgentRequest pythonRequest = PythonAgentRequest.builder()
                    .projectUuid(request.getProjectUuid())
                    .title(request.getTitle())
                    .content(request.getContent())
                    .context(request.getContext())
                    .systemPrompt(promptVersion.getSystemPrompt())
                    .userPromptTemplate(promptVersion.getUserPromptTemplate())
                    .templateUuid(promptVersion.getTemplateUuid())
                    .templateVersion(promptVersion.getVersion())
                    .userId(userId)
                    .rag(ragPayload)
                    .build();

            PythonAgentResponse pythonResponse = pythonAgentClient.invoke(request.getAgentType(), pythonRequest);
            /*
             * 将 Python 服务返回结果序列化为 JSON 字符串，写入运行记录并标记成功。
             */
            JsonNode execution = pythonResponse.getData();
            if (execution == null || !"SUCCESS".equals(execution.path("status").asText())
                    || !execution.has("mock")) {
                throw new BusinessException(ErrorCode.PYTHON_INVALID_RESPONSE);
            }
            String outputContent = execution.path("output").isMissingNode() || execution.path("output").isNull()
                    ? null
                    : objectMapper.writeValueAsString(execution.path("output"));

            agentRun.setOutputContent(outputContent);
            agentRun.setErrorMessage(null);
            agentRun.setStatus(AgentRunStatus.SUCCESS.name());
            agentRun.setTimeTakenMs(execution.path("latency_ms").canConvertToLong()
                    ? execution.path("latency_ms").longValue() : System.currentTimeMillis() - startTime);
            agentRun.setProvider(textOrNull(execution, "provider"));
            agentRun.setModelName(textOrNull(execution, "model"));
            agentRun.setMockState(execution.path("mock").asBoolean() ? "TRUE" : "FALSE");
            agentRun.setTraceId(pythonResponse.getTraceId());
            agentRun.setRawOutputRef(textOrNull(execution, "raw_output_ref"));
            agentRun.setRagStatus(execution.path("rag_status").asText(agentRun.getRagStatus()));
            recordActualRagSnapshot(agentRun, execution.path("used_references"));
            agentRun.setUpdatedAt(LocalDateTime.now());
            agentRunMapper.updateById(agentRun);
            observability.ragRunPersisted(agentRun.getRagStatus(), agentRun.getMockState());
            retrievalRecordService.recordSelected(agentRun, execution.path("used_references"), sha256(request.getContent()));
            recordMetric(agentRun, execution, "AVAILABLE");

            log.info("[Agent] run succeeded userId={} projectId={} projectUuid={} runUuid={} agentType={} timeTakenMs={}",
                    userId, agentRun.getProjectId(), agentRun.getProjectUuid(), agentRun.getRunUuid(),
                    request.getAgentType(), agentRun.getTimeTakenMs());
            return toVO(agentRun);
        } catch (BusinessException exception) {
            /*
             * 业务异常处理：记录失败信息并重新抛出，由上层统一处理。
             */
            agentRun.setStatus(AgentRunStatus.FAILED.name());
            agentRun.setErrorMessage(ErrorCode.PYTHON_RESPONSE_FAILED.getMessage());
            agentRun.setErrorCategory(errorCategory(exception));
            agentRun.setTimeTakenMs(System.currentTimeMillis() - startTime);
            agentRun.setUpdatedAt(LocalDateTime.now());
            agentRunMapper.updateById(agentRun);
            observability.ragRunPersisted(agentRun.getRagStatus(), agentRun.getMockState());
            recordMetric(agentRun, null, "UNAVAILABLE");

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
            agentRun.setErrorCategory("INTERNAL");
            agentRun.setTimeTakenMs(System.currentTimeMillis() - startTime);
            agentRun.setUpdatedAt(LocalDateTime.now());
            agentRunMapper.updateById(agentRun);
            observability.ragRunPersisted(agentRun.getRagStatus(), agentRun.getMockState());
            recordMetric(agentRun, null, "UNAVAILABLE");

            log.error("[Agent] run failed userId={} projectId={} projectUuid={} runUuid={} agentType={} errorCode={} exceptionType={} timeTakenMs={}",
                    userId, agentRun.getProjectId(), agentRun.getProjectUuid(), agentRun.getRunUuid(),
                    request.getAgentType(), ErrorCode.AGENT_RUN_ERROR.getCode(),
                    exception.getClass().getSimpleName(), agentRun.getTimeTakenMs());
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
                .provider(agentRun.getProvider())
                .modelName(agentRun.getModelName())
                .mockState(agentRun.getMockState())
                .traceId(agentRun.getTraceId())
                .errorCategory(agentRun.getErrorCategory())
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

    private void recordMetric(AgentRun agentRun, JsonNode execution, String usageState) {
        ModelCallMetric metric = new ModelCallMetric();
        metric.setAgentRunId(agentRun.getId());
        metric.setWorkflowRunId(agentRun.getWorkflowRunId());
        metric.setStepRunId(agentRun.getStepRunId());
        metric.setPromptVersionId(agentRun.getPromptVersionId());
        metric.setProvider(agentRun.getProvider());
        metric.setModelName(agentRun.getModelName());
        metric.setLatencyMs(agentRun.getTimeTakenMs());
        metric.setMockState(agentRun.getMockState() == null ? "UNKNOWN" : agentRun.getMockState());
        metric.setStatus(agentRun.getStatus());
        metric.setUsageState(usageState);
        metric.setErrorCategory(agentRun.getErrorCategory());
        metric.setTraceId(agentRun.getTraceId());
        metric.setCreatedAt(LocalDateTime.now());
        if (execution != null && execution.path("usage").isObject()) {
            JsonNode usage = execution.path("usage");
            metric.setInputTokens(usage.path("input_tokens").canConvertToInt() ? usage.path("input_tokens").intValue() : null);
            metric.setOutputTokens(usage.path("output_tokens").canConvertToInt() ? usage.path("output_tokens").intValue() : null);
            if (usage.path("estimated_cost").isNumber()) {
                metric.setEstimatedCost(usage.path("estimated_cost").decimalValue());
            }
            metric.setUsageState(metric.getInputTokens() == null && metric.getOutputTokens() == null && metric.getEstimatedCost() == null
                    ? "UNAVAILABLE" : "PARTIAL");
        }
        modelCallMetricService.record(metric);
    }

    private String textOrNull(JsonNode node, String field) {
        return node.path(field).isTextual() && !node.path(field).asText().isBlank() ? node.path(field).asText() : null;
    }

    private void recordActualRagSnapshot(AgentRun run, JsonNode usedReferences) {
        if (!Boolean.TRUE.equals(run.getRagEnabled())) {
            return;
        }
        var snapshot = objectMapper.createObjectNode();
        try {
            JsonNode pendingSnapshot = objectMapper.readTree(run.getRagContextSnapshot());
            if (pendingSnapshot.isObject()) {
                snapshot.setAll((com.fasterxml.jackson.databind.node.ObjectNode) pendingSnapshot);
            }
        } catch (Exception ignored) {
            snapshot.put("candidate_count", 0);
        }
        snapshot.remove("sources");
        var injected = snapshot.putArray("injected_references");
        if (usedReferences.isArray()) {
            for (JsonNode reference : usedReferences) {
                var safeReference = injected.addObject();
                safeReference.put("chunk_uuid", reference.path("chunk_uuid").asText());
                safeReference.put("document_uuid", reference.path("document_uuid").asText());
                safeReference.put("document_version", reference.path("document_version").asText());
                safeReference.put("rank", reference.path("rank").asInt());
                if (reference.path("score").isNumber()) {
                    safeReference.put("score", reference.path("score").doubleValue());
                }
            }
        }
        run.setRagContextSnapshot(writeJsonSafely(snapshot));
    }

    private Object buildRagPayload(AgentRun run, AgentRunRequest request) {
        if (!Boolean.TRUE.equals(run.getRagEnabled())) return Map.of("rag_enabled", false, "retrieved_chunks", List.of(), "budget_chars", run.getContextBudget());
        try {
            int topK = request.getRagTopK() == null ? 5 : Math.max(1, Math.min(request.getRagTopK(), 20));
            var candidates = retrievalService.retrieve(new RetrievalRequest(run.getProjectId(), request.getContent(), topK, 0.0f, run.getContextBudget()));
            var chunks = candidates.stream().map(candidate -> {
                try {
                    return Map.<String, Object>of("chunk_uuid", candidate.chunkUuid(), "document_uuid", candidate.documentUuid(),
                            "document_version", candidate.documentVersion(), "rank", candidate.rank(), "score", candidate.score(),
                            "text", new String(knowledgeStorage.read(candidate.textReference()), java.nio.charset.StandardCharsets.UTF_8));
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            }).toList();
            run.setRagStatus(chunks.isEmpty() ? "EMPTY" : "AVAILABLE");
            var sources = candidates.stream().map(candidate -> Map.of("document_uuid", candidate.documentUuid(), "document_version", candidate.documentVersion(), "chunk_uuid", candidate.chunkUuid())).toList();
            run.setRagContextSnapshot(writeJsonSafely(Map.of("candidate_count", chunks.size(), "top_k", topK, "budget", run.getContextBudget(), "sources", sources)));
            return Map.of("rag_enabled", true, "retrieved_chunks", chunks, "retrieval_version", run.getRetrievalVersion(), "budget_chars", run.getContextBudget());
        } catch (Exception failure) {
            run.setRagStatus("UNAVAILABLE"); run.setRagContextSnapshot("{\"candidate_count\":0,\"failure\":true}");
            return Map.of("rag_enabled", true, "retrieved_chunks", List.of(), "retrieval_version", run.getRetrievalVersion(), "budget_chars", run.getContextBudget());
        }
    }

    private String sha256(String value) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64); for (byte part : hash) result.append(String.format("%02x", part)); return result.toString();
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private String errorCategory(BusinessException exception) {
        return switch (exception.getCode()) {
            case 50002 -> "PROVIDER_CONFIG";
            case 50201 -> "PROVIDER_TRANSIENT";
            case 50202 -> "PROTOCOL_INVALID";
            case 50203 -> "PROVIDER_REJECTED";
            default -> "INTERNAL";
        };
    }
}
