package com.example.gameworkbench.service;

import com.example.gameworkbench.entity.WorkflowDefinitionVersion;

public interface WorkflowDefinitionVersionService {

    WorkflowDefinitionVersion findActiveDefinition(String workflowKey);
}
