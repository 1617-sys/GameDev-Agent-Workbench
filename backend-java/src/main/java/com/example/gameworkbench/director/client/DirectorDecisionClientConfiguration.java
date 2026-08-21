package com.example.gameworkbench.director.client;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DirectorDecisionClientConfiguration {
    @Bean
    @ConditionalOnProperty(name = "app.director.decision-provider", havingValue = "spring-ai", matchIfMissing = true)
    @ConditionalOnMissingBean(ChatModel.class)
    DirectorDecisionClient unavailableDirectorDecisionClient() {
        return new UnavailableDirectorDecisionClient();
    }
}
