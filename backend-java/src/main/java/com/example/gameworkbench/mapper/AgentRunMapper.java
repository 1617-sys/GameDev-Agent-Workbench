package com.example.gameworkbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.AgentRun;
import com.example.gameworkbench.vo.project.AgentRunTypeSummaryVO;

import java.util.List;

public interface AgentRunMapper extends BaseMapper<AgentRun> {
    List<AgentRunTypeSummaryVO> selectAgentRunTypeSummary(Long userId);
}
