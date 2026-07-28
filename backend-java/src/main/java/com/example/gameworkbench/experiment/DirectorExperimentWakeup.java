package com.example.gameworkbench.experiment;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import com.example.gameworkbench.director.persistence.DirectorRunService;
import com.example.gameworkbench.entity.DirectorRun;
import com.example.gameworkbench.entity.PlayerRun;
import com.example.gameworkbench.mapper.DirectorExperimentRunMapper;
import com.example.gameworkbench.mapper.DirectorRunMapper;
import com.example.gameworkbench.mapper.PlayerRunMapper;
import com.example.gameworkbench.service.impl.PlayerRunCompleted;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class DirectorExperimentWakeup {
    private final DirectorExperimentRunMapper experiments;private final PlayerRunMapper playerRuns;private final DirectorRunMapper runs;private final DirectorRunService service;
    @EventListener public void completed(PlayerRunCompleted event){for(var experiment:experiments.selectByPlayerRun(event.runUuid())){PlayerRun left=playerRuns.selectByUuid(experiment.getBaselinePlayerRunUuid()),right=playerRuns.selectByUuid(experiment.getCandidatePlayerRunUuid());if(left==null||right==null||!terminal(left.getStatus())||!terminal(right.getStatus()))continue;String status="SUCCEEDED".equals(left.getStatus())&&"SUCCEEDED".equals(right.getStatus())?"SUCCEEDED":"PARTIAL_SUCCESS";experiments.complete(experiment.getId(),status,LocalDateTime.now());wake(experiment);}}
    @Scheduled(fixedDelayString="${app.director.experiment-wakeup-delay-ms:30000}")public void recoverCompleted(){experiments.selectReadyToWake(20).forEach(this::wake);}
    private void wake(com.example.gameworkbench.entity.DirectorExperimentRun experiment){DirectorRun run=runs.selectById(experiment.getDirectorRunId());if(run!=null&&"WAITING_EXPERIMENT".equals(run.getStatus())&&!run.getCheckpointJson().contains("pendingToolCall"))service.transition(run.getUserId(),run.getProjectId(),run.getRunUuid(),run.getStateVersion(),"RUNNING",run.getCheckpointJson(),null,null);}
    private boolean terminal(String status){return Set.of("SUCCEEDED","PARTIAL_SUCCESS","FAILED").contains(status);}
}
