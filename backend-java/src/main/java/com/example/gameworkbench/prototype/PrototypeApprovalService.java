package com.example.gameworkbench.prototype;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.director.persistence.DirectorRunService;
import com.example.gameworkbench.dto.prototype.PrototypeApprovalRequest;
import com.example.gameworkbench.entity.DirectorRun;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.PrototypeApproval;
import com.example.gameworkbench.entity.PrototypeVersion;
import com.example.gameworkbench.mapper.DirectorRunMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.PrototypeApprovalMapper;
import com.example.gameworkbench.mapper.PrototypeVersionMapper;
import com.example.gameworkbench.vo.prototype.PrototypeApprovalVO;
import lombok.RequiredArgsConstructor;

/**
 * 不可变原型版本的人工审批边界。
 *
 * <p>每个版本只允许一个最终审批事实；幂等键支持客户端安全重放，请求指纹阻止同一键
 * 表示不同决定。审批成功后会唤醒正在等待该 approvalRef 的 Director。</p>
 */
@Service @RequiredArgsConstructor
public class PrototypeApprovalService {
    private final GameProjectMapper projects;private final PrototypeVersionMapper versions;private final PrototypeApprovalMapper approvals;
    private final DirectorRunMapper runs;private final DirectorRunService directorRuns;
    @Transactional public PrototypeApprovalVO decide(long userId,String projectUuid,String versionUuid,String key,PrototypeApprovalRequest request){
        if(key==null||!key.matches("[A-Za-z0-9._:@/-]{8,128}"))throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        GameProject project=projects.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GameProject>().eq(GameProject::getProjectUuid,projectUuid).eq(GameProject::getUserId,userId));if(project==null)throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        PrototypeVersion version=versions.selectByUuid(versionUuid);if(version==null||!Objects.equals(version.getProjectId(),project.getId()))throw new BusinessException(ErrorCode.FORBIDDEN_PROTOTYPE_VERSION_ACCESS);
        String fingerprint=digest(versionUuid+"\n"+request.getDecision()+"\n"+request.getReason());PrototypeApproval replay=approvals.selectIdempotent(userId,project.getId(),key);
        if(replay!=null){if(!Objects.equals(replay.getRequestFingerprint(),fingerprint))conflict();return vo(replay,true);}
        PrototypeApproval existing=approvals.selectByVersion(versionUuid);if(existing!=null){if(existing.getDecision().equals(request.getDecision())&&existing.getReason().equals(request.getReason())&&Objects.equals(existing.getActorUserId(),userId))return vo(existing,true);conflict();}
        if(!"DRAFT".equals(version.getLifecycleStatus())||version.getDirectorRunUuid()==null)conflict();
        LocalDateTime now=LocalDateTime.now();PrototypeApproval approval=PrototypeApproval.builder().approvalUuid(UUID.randomUUID().toString()).projectId(project.getId()).prototypeVersionUuid(versionUuid).directorRunUuid(version.getDirectorRunUuid()).actorUserId(userId).actorType("USER").decision(request.getDecision()).reason(request.getReason()).idempotencyKey(key).requestFingerprint(fingerprint).createdAt(now).build();
        approvals.insert(approval);if(versions.updateLifecycle(versionUuid,project.getId(),"DRAFT",request.getDecision(),now)!=1)conflict();
        DirectorRun waiting=runs.selectWaitingRef("WAITING_APPROVAL","approval://"+versionUuid);if(waiting!=null&&Objects.equals(waiting.getRunUuid(),version.getDirectorRunUuid()))directorRuns.transition(userId,project.getId(),waiting.getRunUuid(),waiting.getStateVersion(),"RUNNING",waiting.getCheckpointJson(),null,null);
        return vo(approval,false);
    }
    private PrototypeApprovalVO vo(PrototypeApproval a,boolean reused){return PrototypeApprovalVO.builder().approvalUuid(a.getApprovalUuid()).prototypeVersionUuid(a.getPrototypeVersionUuid()).directorRunUuid(a.getDirectorRunUuid()).actorUserId(a.getActorUserId()).actorType(a.getActorType()).decision(a.getDecision()).reason(a.getReason()).createdAt(a.getCreatedAt()).reused(reused).build();}
    private String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private void conflict(){throw new BusinessException(ErrorCode.PROTOTYPE_APPROVAL_CONFLICT);}
}
