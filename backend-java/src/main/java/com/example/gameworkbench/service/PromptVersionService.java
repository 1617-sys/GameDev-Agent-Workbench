package com.example.gameworkbench.service;

import com.example.gameworkbench.entity.PromptVersion;

/**
 * Read-only access to immutable PromptVersion records.
 */
public interface PromptVersionService {

    PromptVersion findActiveByAgentType(String agentType);
}
