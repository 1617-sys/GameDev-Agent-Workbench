package com.example.gameworkbench.director.application;

import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.director.persistence.CreateDirectorRunCommand;
import com.example.gameworkbench.director.persistence.DirectorRunService;
import com.example.gameworkbench.director.persistence.DirectorRunView;
import com.example.gameworkbench.dto.director.SubmitDirectorRunRequest;
import com.example.gameworkbench.entity.DirectorRun;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.DirectorRunMapper;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class DirectorApplicationService {
    private final GameProjectMapper projects;private final DirectorRunService runs;private final DirectorRunMapper runRows;private final ApplicationEventPublisher events;
    @Transactional public DirectorRun submit(long userId,String projectUuid,String key,String traceId,SubmitDirectorRunRequest request){GameProject project=owned(userId,projectUuid);DirectorRun run=runs.create(userId,project.getId(),new CreateDirectorRunCommand(key,request.getGoal(),request.getBudget(),request.getFacts()));if("PENDING".equals(run.getStatus())){String trace=traceId!=null&&traceId.matches("[A-Za-z0-9._:-]{8,64}")?traceId:UUID.randomUUID().toString();run=runs.transition(userId,project.getId(),run.getRunUuid(),run.getStateVersion(),"RUNNING",run.getCheckpointJson(),null,null);runRows.setTrace(run.getRunUuid(),trace);run.setTraceId(trace);events.publishEvent(new DirectorRunRequested(run.getRunUuid()));}return run;}
    public DirectorRunView get(long userId,String projectUuid,String runUuid){GameProject project=owned(userId,projectUuid);return runs.get(userId,project.getId(),runUuid);}
    @Transactional public DirectorRun cancel(long userId,String projectUuid,String runUuid,long expectedVersion){GameProject project=owned(userId,projectUuid);DirectorRunView view=runs.get(userId,project.getId(),runUuid);return runs.transition(userId,project.getId(),runUuid,expectedVersion,"CANCELED",view.run().getCheckpointJson(),null,"USER_CANCELED");}
    private GameProject owned(long userId,String uuid){GameProject p=projects.selectOne(new LambdaQueryWrapper<GameProject>().eq(GameProject::getProjectUuid,uuid).eq(GameProject::getUserId,userId));if(p==null)throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);return p;}
}
