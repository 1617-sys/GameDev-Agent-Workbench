package com.example.gameworkbench.prototype;

import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.prototype.TunePrototypeVersionRequest;
import com.example.gameworkbench.entity.DirectorRun;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.mapper.DirectorRunMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.PrototypeVersionMapper;
import com.example.gameworkbench.service.PrototypeVersionService;
import com.example.gameworkbench.vo.prototype.PrototypeVersionVO;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class PrototypeDraftService {
    private final GameProjectMapper projects;private final PrototypeVersionMapper versions;private final DirectorRunMapper runs;
    private final PrototypeVersionService prototypes;private final Validator validator;
    @Transactional public PrototypeVersionVO create(long userId,long projectId,String runUuid,String parentUuid,String key,TunePrototypeVersionRequest tuning){
        GameProject project=projects.selectById(projectId);if(project==null||!Objects.equals(project.getUserId(),userId))throw new BusinessException(ErrorCode.FORBIDDEN_PROJECT_ACCESS);
        DirectorRun run=runs.selectByUuid(runUuid);if(run==null||!Objects.equals(run.getProjectId(),projectId)||!Objects.equals(run.getUserId(),userId))throw new BusinessException(ErrorCode.DIRECTOR_RUN_NOT_FOUND);
        if(!validator.validate(tuning).isEmpty())throw new BusinessException(ErrorCode.PROTOTYPE_TUNING_INVALID);
        PrototypeVersionVO created=prototypes.tune(userId,project.getProjectUuid(),parentUuid,key,tuning);
        var persisted=versions.selectByUuid(created.getVersionUuid());
        if(persisted.getDirectorRunUuid()!=null){if(!Objects.equals(persisted.getDirectorRunUuid(),runUuid))throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);return prototypes.get(userId,project.getProjectUuid(),created.getVersionUuid());}
        if(versions.markDraft(created.getVersionUuid(),projectId,runUuid,LocalDateTime.now())!=1)throw new BusinessException(ErrorCode.DIRECTOR_RUN_CONCURRENT_UPDATE);
        return prototypes.get(userId,project.getProjectUuid(),created.getVersionUuid());
    }
}
