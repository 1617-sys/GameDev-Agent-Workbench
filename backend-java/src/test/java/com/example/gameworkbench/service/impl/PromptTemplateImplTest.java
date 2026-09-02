package com.example.gameworkbench.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.gameworkbench.entity.PromptTemplate;
import com.example.gameworkbench.mapper.PromptTemplateMapper;
import com.example.gameworkbench.mapper.PromptTemplateAuditMapper;
import com.example.gameworkbench.entity.PromptTemplateAudit;
import com.example.gameworkbench.dto.promptTemplate.PromptTemplateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class PromptTemplateImplTest {
    @Test
    void listsDiscoverableTemplatesWithBoundedPagination() {
        PromptTemplateMapper mapper = mock(PromptTemplateMapper.class);
        Page<PromptTemplate> result = new Page<>(1, 20, 1);
        result.setRecords(List.of(PromptTemplate.builder().templateUuid("template-1").agentType("DESIGNER").name("Designer").status("ACTIVE").build()));
        when(mapper.selectPage(any(), any())).thenReturn(result);

        var page = new PromptTemplateImpl(mapper, mock(PromptTemplateAuditMapper.class), new ObjectMapper().findAndRegisterModules()).list(7L, 1, 20, "DESIGNER", "ACTIVE");

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).extracting("templateUuid").containsExactly("template-1");
    }

    @Test
    void updateWritesActorAndBeforeAfterAudit() {
        PromptTemplateMapper mapper = mock(PromptTemplateMapper.class);
        PromptTemplateAuditMapper audits = mock(PromptTemplateAuditMapper.class);
        PromptTemplate existing = PromptTemplate.builder().id(1L).templateUuid("template-1").agentType("GAME_CONCEPT")
                .name("Designer").systemPrompt("old").userPromptTemplate("user").version(1).status("ACTIVE").build();
        when(mapper.selectOne(any())).thenReturn(existing);
        when(mapper.update(any(), any())).thenReturn(1);
        PromptTemplateRequest request = new PromptTemplateRequest(); request.setAgentType("GAME_CONCEPT"); request.setName("Designer");
        request.setSystemPrompt("new"); request.setUserPromptTemplate("user"); request.setVersion(2); request.setStatus("ACTIVE");

        new PromptTemplateImpl(mapper, audits, new ObjectMapper().findAndRegisterModules()).updatePromptTemplate(7L, "template-1", request);

        ArgumentCaptor<PromptTemplateAudit> audit = ArgumentCaptor.forClass(PromptTemplateAudit.class);
        verify(audits).insert(audit.capture());
        assertThat(audit.getValue().getActorUserId()).isEqualTo(7L);
        assertThat(audit.getValue().getBeforeJson()).contains("old");
        assertThat(audit.getValue().getAfterJson()).contains("new");
    }
}
