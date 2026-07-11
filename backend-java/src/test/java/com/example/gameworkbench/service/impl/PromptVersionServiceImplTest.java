package com.example.gameworkbench.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.gameworkbench.entity.PromptVersion;
import com.example.gameworkbench.mapper.PromptVersionMapper;
import com.example.gameworkbench.service.PromptVersionService;

@ExtendWith(MockitoExtension.class)
class PromptVersionServiceImplTest {

    @Mock
    private PromptVersionMapper promptVersionMapper;

    @Test
    void shouldReturnTheLatestActivePromptVersionForAgentType() {
        PromptVersion activeVersion = PromptVersion.builder()
                .id(10L)
                .agentType("GAME_CONCEPT")
                .version(1)
                .status("ACTIVE")
                .build();
        when(promptVersionMapper.selectActiveByAgentType("GAME_CONCEPT")).thenReturn(activeVersion);

        PromptVersionServiceImpl service = new PromptVersionServiceImpl(promptVersionMapper);

        assertThat(service.findActiveByAgentType("GAME_CONCEPT")).isSameAs(activeVersion);
        verify(promptVersionMapper).selectActiveByAgentType("GAME_CONCEPT");
    }

    @Test
    void activeQueryShouldFilterByStatusAndPreferTheLatestVersion() throws Exception {
        Select select = PromptVersionMapper.class
                .getMethod("selectActiveByAgentType", String.class)
                .getAnnotation(Select.class);

        assertThat(select.value()[0])
                .contains("status = 'ACTIVE'")
                .contains("deleted = 0")
                .contains("order by version desc, id desc")
                .contains("limit 1");
    }

    @Test
    void promptVersionServiceShouldExposeNoUpdateOperation() {
        assertThat(Arrays.stream(PromptVersionService.class.getMethods())
                .map(Method::getName))
                .doesNotContain("update", "updateById", "modify");
    }
}
