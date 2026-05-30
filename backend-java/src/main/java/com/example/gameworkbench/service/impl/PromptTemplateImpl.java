package com.example.gameworkbench.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.dto.promptTemplate.PromptTemplateRequest;
import com.example.gameworkbench.entity.PromptTemplate;
import com.example.gameworkbench.mapper.PromptTemplateMapper;
import com.example.gameworkbench.service.PromptTemplateService;
import com.example.gameworkbench.vo.prompt.PromptTemplateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateImpl implements PromptTemplateService {

    private final PromptTemplateMapper promptTemplateMapper;
    @Override
    public PromptTemplateVO modifyPromptTemplate(Long userId, PromptTemplateRequest request) {
        //用户传进新的提示词，修改模板提示词，返回新的提示词

        PromptTemplate promptTemplate = PromptTemplate.builder()
                .version(request.getVersion())
                .userPromptTemplate(request.getUserPrompt())
                .systemPrompt(request.getSystemPrompt())
                .templateUuid(UUID.randomUUID().toString())
                .name(request.getName())
                .agentType(request.getAgentType())
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        promptTemplateMapper.insert(promptTemplate);
        return toVo(promptTemplate);
    }

    @Override
    public PromptTemplateVO getPromptTemplate(Long userId, PromptTemplateRequest request) {
        PromptTemplate promptTemplate = promptTemplateMapper.selectOne(new LambdaQueryWrapper<PromptTemplate>()
                .eq(PromptTemplate::getTemplateUuid, request.getTemplateUuid()));
        return toVo(promptTemplate);
    }

    private PromptTemplateVO toVo(PromptTemplate promptTemplate) {
        return PromptTemplateVO.builder()
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
