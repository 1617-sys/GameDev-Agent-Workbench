package com.example.gameworkbench.service.impl;

import org.springframework.stereotype.Service;

import com.example.gameworkbench.entity.WorkflowDefinitionVersion;
import com.example.gameworkbench.mapper.WorkflowDefinitionVersionMapper;
import com.example.gameworkbench.service.WorkflowDefinitionVersionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowDefinitionVersionServiceImpl implements WorkflowDefinitionVersionService {

    private final WorkflowDefinitionVersionMapper workflowDefinitionVersionMapper;

    @Override
    public WorkflowDefinitionVersion findActiveDefinition(String workflowKey) {
        return workflowDefinitionVersionMapper.selectActiveByWorkflowKey(workflowKey);
    }
}
