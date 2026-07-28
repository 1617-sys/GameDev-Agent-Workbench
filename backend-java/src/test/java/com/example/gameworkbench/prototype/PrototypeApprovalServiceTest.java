package com.example.gameworkbench.prototype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.director.persistence.DirectorRunService;
import com.example.gameworkbench.dto.prototype.PrototypeApprovalRequest;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.PrototypeApproval;
import com.example.gameworkbench.entity.PrototypeVersion;
import com.example.gameworkbench.mapper.DirectorRunMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.PrototypeApprovalMapper;
import com.example.gameworkbench.mapper.PrototypeVersionMapper;

class PrototypeApprovalServiceTest {
    @Test void recordsRealUserEvidenceAndTransitionsDraft(){Fixture f=fixture();when(f.versions.updateLifecycle(any(),any(),any(),any(),any())).thenReturn(1);PrototypeApprovalRequest request=request("APPROVED","verified playtest");var result=f.service.decide(7L,"project","draft","approval-key-001",request);assertThat(result.getActorType()).isEqualTo("USER");ArgumentCaptor<PrototypeApproval> row=ArgumentCaptor.forClass(PrototypeApproval.class);verify(f.approvals).insert(row.capture());assertThat(row.getValue().getActorUserId()).isEqualTo(7L);assertThat(row.getValue().getDirectorRunUuid()).isEqualTo("director");verify(f.versions).updateLifecycle(any(),any(),org.mockito.ArgumentMatchers.eq("DRAFT"),org.mockito.ArgumentMatchers.eq("APPROVED"),any());}
    @Test void conflictingSecondDecisionIsRejected(){Fixture f=fixture();when(f.approvals.selectByVersion("draft")).thenReturn(PrototypeApproval.builder().actorUserId(7L).decision("APPROVED").reason("first").build());assertThatThrownBy(()->f.service.decide(7L,"project","draft","approval-key-002",request("REJECTED","changed"))).isInstanceOf(BusinessException.class);}
    private Fixture fixture(){GameProjectMapper projects=mock(GameProjectMapper.class);PrototypeVersionMapper versions=mock(PrototypeVersionMapper.class);PrototypeApprovalMapper approvals=mock(PrototypeApprovalMapper.class);DirectorRunMapper runs=mock(DirectorRunMapper.class);DirectorRunService service=mock(DirectorRunService.class);GameProject project=new GameProject();project.setId(1L);project.setUserId(7L);project.setProjectUuid("project");when(projects.selectOne(any())).thenReturn(project);when(versions.selectByUuid("draft")).thenReturn(PrototypeVersion.builder().versionUuid("draft").projectId(1L).lifecycleStatus("DRAFT").directorRunUuid("director").build());return new Fixture(new PrototypeApprovalService(projects,versions,approvals,runs,service),versions,approvals);}
    private PrototypeApprovalRequest request(String decision,String reason){PrototypeApprovalRequest out=new PrototypeApprovalRequest();out.setDecision(decision);out.setReason(reason);return out;}
    private record Fixture(PrototypeApprovalService service,PrototypeVersionMapper versions,PrototypeApprovalMapper approvals){}
}
