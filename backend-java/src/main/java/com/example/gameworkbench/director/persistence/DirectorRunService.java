package com.example.gameworkbench.director.persistence;

import com.example.gameworkbench.entity.DirectorRun;

public interface DirectorRunService {
    DirectorRun create(long userId,long projectId,CreateDirectorRunCommand command);
    DirectorRun appendDecision(long userId,long projectId,String runUuid,AppendDirectorDecisionCommand command);
    DirectorRun transition(long userId,long projectId,String runUuid,long expectedVersion,String targetStatus,
        String checkpoint,String approvalRef,String errorCode);
    DirectorRunView get(long userId,long projectId,String runUuid);
}
