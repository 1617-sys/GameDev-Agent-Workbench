package com.example.gameworkbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.vo.project.ProjectRunSummaryVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface GameProjectMapper extends BaseMapper<GameProject> {
    List<ProjectRunSummaryVO> selectProjectRunSummary(@Param("userId") Long userId);
}
