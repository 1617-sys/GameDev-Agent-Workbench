package com.example.gameworkbench.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.*;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.*;
import com.example.gameworkbench.export.*;
import com.example.gameworkbench.export.FrozenPrototypeExport.FrozenArtifact;
import com.example.gameworkbench.mapper.*;
import com.example.gameworkbench.service.PrototypeExportService;
import com.example.gameworkbench.vo.export.PrototypeExportJobVO;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import lombok.RequiredArgsConstructor;

/**
 * 将不可变原型及其设计、遥测和调优证据冻结为可下载包。
 *
 * <p>导出使用 frozenInputJson 固定输入，并在下载前重新校验 package digest。任何缺失的
 * 来源链、内容摘要不一致或敏感内容都会使整个导出失败，而不是生成部分可信的包。</p>
 */
@Service @RequiredArgsConstructor
public class PrototypeExportServiceImpl implements PrototypeExportService {
 private static final String OPERATION="EXPORT_PROTOTYPE",FORMAT="1.0";
 private static final Pattern KEY=Pattern.compile("^[A-Za-z0-9._~-]{1,128}$");
 private final GameProjectMapper projects; private final PrototypeVersionMapper versions; private final AgentArtifactMapper artifacts;
 private final PrototypePlaytestAggregateMapper aggregates; private final PrototypeExportJobMapper jobs;
 private final PrototypePackageBuilder builder; private final ObjectMapper json;

 @Override public PrototypeExportJobVO create(Long userId,String projectUuid,String versionUuid,String key){
  requireKey(key);GameProject project=ownedProject(userId,projectUuid);PrototypeVersion version=ownedVersion(project.getId(),versionUuid);
  FrozenPrototypeExport frozen=freeze(project,version);String frozenJson=canonical(json.valueToTree(frozen));String fingerprint=digest(frozenJson);
  PrototypeExportJob replay=jobs.selectIdempotent(userId,project.getId(),key);if(replay!=null)return replay(replay,fingerprint,true);
  PrototypeExportJob job=PrototypeExportJob.builder().jobUuid(UUID.randomUUID().toString()).userId(userId).projectId(project.getId()).prototypeVersionUuid(versionUuid)
   .operation(OPERATION).idempotencyKey(key).requestFingerprint(fingerprint).frozenInputJson(frozenJson).status("PENDING").attemptCount(0).createdAt(LocalDateTime.now()).build();
  try{jobs.insert(job);}catch(DuplicateKeyException conflict){PrototypeExportJob concurrent=jobs.selectIdempotent(userId,project.getId(),key);if(concurrent==null)throw conflict;return replay(concurrent,fingerprint,true);}
  execute(job);return vo(job,false);
 }
 @Override public PrototypeExportJobVO get(Long userId,String projectUuid,String uuid){return vo(ownedJob(userId,projectUuid,uuid),false);}
 @Override public PrototypeExportJobVO retry(Long userId,String projectUuid,String uuid){PrototypeExportJob job=ownedJob(userId,projectUuid,uuid);if("COMPLETED".equals(job.getStatus()))return vo(job,true);if(job.getAttemptCount()>=3)throw new BusinessException(ErrorCode.EXPORT_RETRY_EXHAUSTED);execute(job);return vo(job,true);}
 @Override public PrototypeExportJob download(Long userId,String projectUuid,String uuid){PrototypeExportJob job=ownedJob(userId,projectUuid,uuid);if(!"COMPLETED".equals(job.getStatus())||job.getPackageBytes()==null)throw new BusinessException(ErrorCode.EXPORT_NOT_READY);if(!digest(job.getPackageBytes()).equals(job.getPackageDigest()))throw new BusinessException(ErrorCode.EXPORT_SECURITY_REJECTED);return job;}

 private void execute(PrototypeExportJob job){job.setAttemptCount(job.getAttemptCount()+1);job.setErrorCode(null);job.setStatus("PENDING");jobs.updateById(job);try{
   FrozenPrototypeExport frozen=json.readValue(job.getFrozenInputJson(),FrozenPrototypeExport.class);byte[] bytes=builder.build(frozen,job.getRequestFingerprint());
   job.setPackageName("prototype-v"+frozen.versionNumber()+"-"+frozen.versionUuid()+".zip");job.setPackageBytes(bytes);job.setPackageSize((long)bytes.length);job.setPackageDigest(digest(bytes));job.setStatus("COMPLETED");job.setCompletedAt(LocalDateTime.now());
  }catch(Exception failure){job.setStatus("FAILED");job.setPackageBytes(null);job.setPackageSize(null);job.setPackageDigest(null);job.setCompletedAt(null);job.setErrorCode(failure instanceof BusinessException?"EXPORT_SECURITY_REJECTED":"EXPORT_BUILD_FAILED");}jobs.updateById(job);}

 private FrozenPrototypeExport freeze(GameProject p,PrototypeVersion v){
  AgentArtifact config=required(artifacts.selectByArtifactUuid(v.getGameConfigArtifactUuid()));verify(config);if(!v.getConfigDigest().equals(config.getContentDigest()))incomplete();
  AgentArtifact manifest=required(artifacts.selectLatestProjectTypeSource(p.getId(),ArtifactType.RESOURCE_MANIFEST.name(),config.getArtifactUuid()));verify(manifest);
  AgentArtifact origin=origin(config,p.getId());String brief=artifacts.selectWorkflowInputByOriginStep(origin.getStepRunId());if(brief==null||brief.isBlank())incomplete();
  AgentArtifact concept=required(artifacts.selectWorkflowArtifactByOriginStep(origin.getStepRunId(),ArtifactType.GAME_CONCEPT_RESULT.name()));verify(concept);
  AgentArtifact loop=required(artifacts.selectWorkflowArtifactByOriginStep(origin.getStepRunId(),ArtifactType.CORE_LOOP_DESIGN_RESULT.name()));verify(loop);
  AgentArtifact tasks=required(artifacts.selectWorkflowArtifactByOriginStep(origin.getStepRunId(),ArtifactType.TASK_BREAKDOWN_RESULT.name()));verify(tasks);
  AgentArtifact suggestion=required(artifacts.selectLatestProjectTypeSource(p.getId(),ArtifactType.BALANCE_SUGGESTION.name(),config.getArtifactUuid()));verify(suggestion);
  PrototypePlaytestAggregate aggregate=aggregates.selectByVersion(v.getVersionUuid());if(aggregate==null||aggregate.getSnapshotAt()==null||aggregate.getEndedSessionCount()<1)incomplete();
  ObjectNode metrics=json.createObjectNode();metrics.put("prototypeVersionUuid",v.getVersionUuid());metrics.put("sampleSize",aggregate.getEndedSessionCount());metrics.put("won",aggregate.getWonCount());metrics.put("lost",aggregate.getLostCount());metrics.put("abandoned",aggregate.getAbandonedCount());metrics.put("totalDurationMs",aggregate.getTotalDurationMs());metrics.put("totalScore",aggregate.getTotalScore());metrics.put("totalHitCount",aggregate.getTotalHitCount());metrics.put("totalCollectedCount",aggregate.getTotalCollectedCount());metrics.put("totalRestartCount",aggregate.getTotalRestartCount());metrics.put("healthDepleted",aggregate.getHealthDepletedCount());metrics.put("timeExpired",aggregate.getTimeExpiredCount());metrics.put("snapshotAt",aggregate.getSnapshotAt().toString());String summary=canonical(metrics);
  Map<String,FrozenArtifact> design=new TreeMap<>();design.put("gameConcept",frozen(concept));design.put("coreLoop",frozen(loop));design.put("tasks",frozen(tasks));
  return new FrozenPrototypeExport(FORMAT,p.getId(),p.getProjectUuid(),p.getName(),brief,v.getVersionUuid(),v.getVersionNumber(),v.getCreatedAt(),config.getArtifactUuid(),config.getContentDigest(),config.getContent(),manifest.getArtifactUuid(),manifest.getContentDigest(),manifest.getContent(),v.getRuntimeCapabilityVersion(),aggregate.getSnapshotAt().toString(),summary,digest(summary),design,frozen(suggestion));
 }
 private AgentArtifact origin(AgentArtifact artifact,Long projectId){Set<String> seen=new HashSet<>();AgentArtifact current=artifact;while(current.getStepRunId()==null){if(current.getArtifactUuid()==null||!seen.add(current.getArtifactUuid())||current.getSourceArtifactUuid()==null)incomplete();current=required(artifacts.selectByArtifactUuid(current.getSourceArtifactUuid()));if(!projectId.equals(current.getProjectId()))incomplete();verify(current);}return current;}
 private FrozenArtifact frozen(AgentArtifact a){return new FrozenArtifact(a.getArtifactUuid(),a.getContentDigest(),a.getContent());}
 private AgentArtifact required(AgentArtifact a){if(a==null)incomplete();return a;}
 private void verify(AgentArtifact a){if(a.getContent()==null||a.getContentDigest()==null||!digest(a.getContent()).equals(a.getContentDigest()))incomplete();}
 private PrototypeExportJobVO replay(PrototypeExportJob job,String fingerprint,boolean reused){if(!job.getRequestFingerprint().equals(fingerprint))throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);return vo(job,reused);}
 private PrototypeExportJob ownedJob(Long userId,String projectUuid,String uuid){GameProject p=ownedProject(userId,projectUuid);PrototypeExportJob j=jobs.selectByUuid(uuid);if(j==null)throw new BusinessException(ErrorCode.PROTOTYPE_EXPORT_NOT_FOUND);if(!j.getUserId().equals(userId)||!j.getProjectId().equals(p.getId()))throw new BusinessException(ErrorCode.FORBIDDEN_PROJECT_ACCESS);return j;}
 private GameProject ownedProject(Long userId,String uuid){if(userId==null)throw new BusinessException(ErrorCode.UNAUTHORIZED);GameProject p=projects.selectOne(new LambdaQueryWrapper<GameProject>().eq(GameProject::getProjectUuid,uuid).eq(GameProject::getUserId,userId));if(p==null)throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);return p;}
 private PrototypeVersion ownedVersion(Long projectId,String uuid){PrototypeVersion v=versions.selectByUuid(uuid);if(v==null)throw new BusinessException(ErrorCode.PROTOTYPE_VERSION_NOT_FOUND);if(!v.getProjectId().equals(projectId))throw new BusinessException(ErrorCode.FORBIDDEN_PROTOTYPE_VERSION_ACCESS);return v;}
 private PrototypeExportJobVO vo(PrototypeExportJob j,boolean reused){return PrototypeExportJobVO.builder().jobUuid(j.getJobUuid()).prototypeVersionUuid(j.getPrototypeVersionUuid()).status(j.getStatus()).packageName(j.getPackageName()).packageDigest(j.getPackageDigest()).packageSize(j.getPackageSize()).attemptCount(j.getAttemptCount()).errorCode(j.getErrorCode()).createdAt(j.getCreatedAt()).completedAt(j.getCompletedAt()).reused(reused).build();}
 private void requireKey(String key){if(key==null||!KEY.matcher(key).matches())throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);}private void incomplete(){throw new BusinessException(ErrorCode.EXPORT_INPUT_INCOMPLETE);}
 private String canonical(JsonNode node){try{return json.writeValueAsString(sort(node));}catch(Exception e){throw new IllegalStateException(e);}}private JsonNode sort(JsonNode n){if(n.isObject()){ObjectNode o=json.createObjectNode();TreeSet<String> keys=new TreeSet<>();n.fieldNames().forEachRemaining(keys::add);keys.forEach(k->o.set(k,sort(n.get(k))));return o;}if(n.isArray()){ArrayNode a=json.createArrayNode();n.forEach(v->a.add(sort(v)));return a;}return n;}
 private String digest(String value){return digest(value.getBytes(StandardCharsets.UTF_8));}private String digest(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception e){throw new IllegalStateException(e);}}
}
