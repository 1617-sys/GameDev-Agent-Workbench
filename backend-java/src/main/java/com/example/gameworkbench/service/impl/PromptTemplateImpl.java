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
import com.example.gameworkbench.service.PromptTemplateService;
import com.example.gameworkbench.vo.prompt.PromptTemplateVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateImpl implements PromptTemplateService {

    private final PromptTemplateMapper promptTemplateMapper;

    @Override
    public PromptTemplateVO modifyPromptTemplate(Long userId, PromptTemplateRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        if (!StringUtils.hasText(request.getTemplateUuid())) {
            return createPromptTemplate(request);
        }

        return updatePromptTemplate(request);
    }

    @Override
    public PromptTemplateVO updatePromptTemplate(Long userId, String templateUuid, PromptTemplateRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        request.setTemplateUuid(templateUuid);
        return updatePromptTemplate(request);
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

    private PromptTemplateVO createPromptTemplate(PromptTemplateRequest request) {
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
        return toVo(promptTemplate);
    }

    private PromptTemplateVO updatePromptTemplate(PromptTemplateRequest request) {
        PromptTemplate promptTemplate = promptTemplateMapper.selectOne(new LambdaQueryWrapper<PromptTemplate>()
                .eq(PromptTemplate::getTemplateUuid, request.getTemplateUuid()));
        if (promptTemplate == null) {
            throw new BusinessException(ErrorCode.PROMPT_TEMPLATE_NOT_FOUND);
        }

        promptTemplate.setAgentType(request.getAgentType());
        promptTemplate.setName(request.getName());
        promptTemplate.setSystemPrompt(request.getSystemPrompt());
        promptTemplate.setUserPromptTemplate(request.getUserPromptTemplate());
        promptTemplate.setVersion(request.getVersion());
        promptTemplate.setStatus(normalizeStatus(request.getStatus()));
        promptTemplate.setUpdatedAt(LocalDateTime.now());

        promptTemplateMapper.updateById(promptTemplate);
        return toVo(promptTemplate);
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
