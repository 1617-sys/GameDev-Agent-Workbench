package com.example.gameworkbench.service.impl;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.promptTemplate.PromptTemplateRequest;
import com.example.gameworkbench.entity.PromptTemplate;
import com.example.gameworkbench.mapper.PromptTemplateMapper;
import com.example.gameworkbench.mapper.PromptTemplateAuditMapper;
import com.example.gameworkbench.entity.PromptTemplateAudit;
import com.example.gameworkbench.service.PromptTemplateService;
import com.example.gameworkbench.vo.prompt.PromptTemplateVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateImpl implements PromptTemplateService {

    private final PromptTemplateMapper promptTemplateMapper;
    private final PromptTemplateAuditMapper promptTemplateAuditMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public PromptTemplateVO modifyPromptTemplate(Long userId, PromptTemplateRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        if (!StringUtils.hasText(request.getTemplateUuid())) {
            return createPromptTemplate(userId, request);
        }

        return updatePromptTemplate(userId, request);
    }

    @Override
    @Transactional
    public PromptTemplateVO updatePromptTemplate(Long userId, String templateUuid, PromptTemplateRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        request.setTemplateUuid(templateUuid);
        return updatePromptTemplate(userId, request);
    }

    @Override
    public PromptTemplateVO getPromptTemplate(Long userId, String templateUuid) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        PromptTemplate promptTemplate = promptTemplateMapper.selectOne(new LambdaQueryWrapper<PromptTemplate>()
                .eq(PromptTemplate::getTemplateUuid, templateUuid));
        if (promptTemplate == null) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND);
        }

        return toVo(promptTemplate);
    }

    @Override
    public Page<PromptTemplateVO> list(Long userId, int pageNum, int pageSize, String agentType, String status) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        LambdaQueryWrapper<PromptTemplate> query = new LambdaQueryWrapper<PromptTemplate>()
                .eq(StringUtils.hasText(agentType), PromptTemplate::getAgentType, agentType)
                .eq(StringUtils.hasText(status), PromptTemplate::getStatus, status)
                .orderByDesc(PromptTemplate::getUpdatedAt);
        Page<PromptTemplate> source = promptTemplateMapper.selectPage(new Page<>(safePage, safeSize), query);
        Page<PromptTemplateVO> output = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        output.setRecords(source.getRecords().stream().map(this::toVo).toList());
        return output;
    }

    private PromptTemplateVO createPromptTemplate(Long userId, PromptTemplateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        PromptTemplate promptTemplate = PromptTemplate.builder()
                .templateUuid(UUID.randomUUID().toString())
                .agentType(request.getAgentType())
                .name(request.getName())
                .systemPrompt(request.getSystemPrompt())
                .userPromptTemplate(request.getUserPromptTemplate())
                .version(request.getVersion())
                .status(normalizeStatus(request.getStatus()))
                .createdAt(now)
                .updatedAt(now)
                .build();

        promptTemplateMapper.insert(promptTemplate);
        audit(userId, promptTemplate.getTemplateUuid(), "CREATE", null, promptTemplate);
        return toVo(promptTemplate);
    }

    private PromptTemplateVO updatePromptTemplate(Long userId, PromptTemplateRequest request) {
        PromptTemplate promptTemplate = promptTemplateMapper.selectOne(new LambdaQueryWrapper<PromptTemplate>()
                .eq(PromptTemplate::getTemplateUuid, request.getTemplateUuid()));
        if (promptTemplate == null) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND);
        }
        String before = auditJson(promptTemplate);
        Integer previousVersion = promptTemplate.getVersion();
        if (previousVersion == null || request.getVersion() == null || request.getVersion() != previousVersion + 1) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_VERSION_CONFLICT);
        }

        promptTemplate.setAgentType(request.getAgentType());
        promptTemplate.setName(request.getName());
        promptTemplate.setSystemPrompt(request.getSystemPrompt());
        promptTemplate.setUserPromptTemplate(request.getUserPromptTemplate());
        promptTemplate.setVersion(request.getVersion());
        promptTemplate.setStatus(normalizeStatus(request.getStatus()));
        promptTemplate.setUpdatedAt(LocalDateTime.now());

        int updated = promptTemplateMapper.update(promptTemplate, new LambdaUpdateWrapper<PromptTemplate>()
                .eq(PromptTemplate::getId, promptTemplate.getId())
                .eq(PromptTemplate::getVersion, previousVersion));
        if (updated != 1) throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_VERSION_CONFLICT);
        audit(userId, promptTemplate.getTemplateUuid(), "UPDATE", before, promptTemplate);
        return toVo(promptTemplate);
    }

    private void audit(Long userId, String templateUuid, String operation, String beforeJson, PromptTemplate after) {
        PromptTemplateAudit audit = new PromptTemplateAudit();
        audit.setAuditUuid(UUID.randomUUID().toString()); audit.setTemplateUuid(templateUuid); audit.setActorUserId(userId);
        audit.setOperation(operation); audit.setBeforeJson(beforeJson); audit.setAfterJson(auditJson(after)); audit.setCreatedAt(LocalDateTime.now());
        promptTemplateAuditMapper.insert(audit);
    }

    private String auditJson(PromptTemplate value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Unable to write prompt audit", exception); }
    }

    private String normalizeStatus(String status) {
        return StringUtils.hasText(status) ? status : "ACTIVE";
    }

    private PromptTemplateVO toVo(PromptTemplate promptTemplate) {
        return PromptTemplateVO.builder()
                .id(promptTemplate.getId())
                .templateUuid(promptTemplate.getTemplateUuid())
                .agentType(promptTemplate.getAgentType())
                .name(promptTemplate.getName())
                .systemPrompt(promptTemplate.getSystemPrompt())
                .userPromptTemplate(promptTemplate.getUserPromptTemplate())
                .version(promptTemplate.getVersion())
                .status(promptTemplate.getStatus())
                .createdAt(promptTemplate.getCreatedAt())
                .updatedAt(promptTemplate.getUpdatedAt())
                .build();
    }
}
