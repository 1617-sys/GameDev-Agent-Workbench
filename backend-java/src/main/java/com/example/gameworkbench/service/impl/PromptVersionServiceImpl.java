package com.example.gameworkbench.service.impl;

import org.springframework.stereotype.Service;

import com.example.gameworkbench.entity.PromptVersion;
import com.example.gameworkbench.mapper.PromptVersionMapper;
import com.example.gameworkbench.service.PromptVersionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PromptVersionServiceImpl implements PromptVersionService {

    private final PromptVersionMapper promptVersionMapper;

    @Override
    public PromptVersion findActiveByAgentType(String agentType) {
        return promptVersionMapper.selectActiveByAgentType(agentType);
    }
}
