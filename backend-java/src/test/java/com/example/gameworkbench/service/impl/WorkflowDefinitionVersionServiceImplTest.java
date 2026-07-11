package com.example.gameworkbench.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.annotations.Select;

import com.example.gameworkbench.entity.WorkflowDefinitionVersion;
import com.example.gameworkbench.mapper.WorkflowDefinitionVersionMapper;

@ExtendWith(MockitoExtension.class)
class WorkflowDefinitionVersionServiceImplTest {

    @Mock
    private WorkflowDefinitionVersionMapper workflowDefinitionVersionMapper;

    @Test
    void shouldReturnTheActiveDefinitionForWorkflowKey() {
        WorkflowDefinitionVersion activeDefinition = WorkflowDefinitionVersion.builder()
                .id(1L)
                .workflowKey("GAME_DESIGN")
                .version(1)
                .status("ACTIVE")
                .build();
        when(workflowDefinitionVersionMapper.selectActiveByWorkflowKey("GAME_DESIGN"))
                .thenReturn(activeDefinition);

        WorkflowDefinitionVersionServiceImpl service =
                new WorkflowDefinitionVersionServiceImpl(workflowDefinitionVersionMapper);

        assertThat(service.findActiveDefinition("GAME_DESIGN")).isSameAs(activeDefinition);
        verify(workflowDefinitionVersionMapper).selectActiveByWorkflowKey("GAME_DESIGN");
    }

    @Test
    void activeDefinitionQueryShouldFilterByActiveStatusAndPreferTheLatestVersion() throws Exception {
        Select select = WorkflowDefinitionVersionMapper.class
                .getMethod("selectActiveByWorkflowKey", String.class)
                .getAnnotation(Select.class);

        assertThat(select.value()[0])
                .contains("status = 'ACTIVE'")
                .contains("order by version desc")
                .contains("limit 1");
    }
}
