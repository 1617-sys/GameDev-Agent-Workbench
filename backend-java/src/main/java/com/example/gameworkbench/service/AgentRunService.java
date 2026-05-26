package com.example.gameworkbench.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.gameworkbench.common.enums.AgentRunStatus;
import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.dto.agent.AgentRunRequest;
import com.example.gameworkbench.vo.agent.AgentRunVO;

public interface AgentRunService {

    AgentRunVO run(Long userId, AgentRunRequest request);

    AgentRunVO getRun(Long userId, String runUuid);

    Page<AgentRunVO> listRuns(
            Long userId,
            Integer pageNum,
            Integer pageSize,
            String projectUuid,
            AgentType agentType,
            AgentRunStatus status
    );
}
