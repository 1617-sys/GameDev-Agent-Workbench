package com.example.gameworkbench.service;

import com.example.gameworkbench.dto.promptTemplate.PromptTemplateRequest;
import com.example.gameworkbench.vo.prompt.PromptTemplateVO;

public interface PromptTemplateService {

    PromptTemplateVO modifyPromptTemplate(Long userId, PromptTemplateRequest request);

    PromptTemplateVO updatePromptTemplate(Long userId, String templateUuid, PromptTemplateRequest request);

    PromptTemplateVO getPromptTemplate(Long userId, String templateUuid);
}
