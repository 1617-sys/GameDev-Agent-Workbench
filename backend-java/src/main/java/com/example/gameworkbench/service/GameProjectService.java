package com.example.gameworkbench.service;

import com.example.gameworkbench.dto.gameProject.GameProjectRequest;
import com.example.gameworkbench.vo.project.GameProjectVO;

import java.util.List;

public interface GameProjectService {

    GameProjectVO createProject(Long userId, GameProjectRequest request);

    List<GameProjectVO> listProjects(Long userId);

    GameProjectVO getProject(Long userId, String projectUuid);

    GameProjectVO updateProject(Long userId, String projectUuid, GameProjectRequest request);
}
