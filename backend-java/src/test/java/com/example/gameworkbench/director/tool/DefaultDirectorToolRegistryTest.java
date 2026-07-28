package com.example.gameworkbench.director.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.example.gameworkbench.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;

class DefaultDirectorToolRegistryTest {
    private final ObjectMapper json=new ObjectMapper();
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    @AfterEach void close(){executor.shutdownNow();}

    @Test void discoversStableDefinitionsExecutesReadAndStoresFullResult(){
        DirectorReadModelGateway gateway=mock(DirectorReadModelGateway.class);when(gateway.getPrototypeVersion(9L,"v1")).thenReturn(json.createObjectNode().put("versionUuid","v1").put("config","x".repeat(5000)));
        InMemoryDirectorToolResultStore store=new InMemoryDirectorToolResultStore();var registry=registry(gateway,(u,p,n,a)->true,store);
        assertThat(registry.discover()).extracting(DirectorToolDefinition::name).containsExactly("COMPARE_PROTOTYPE_CONFIGS","GET_MACHINE_EPISODE_METRICS","GET_PLAYER_RUN_STATUS","GET_PROTOTYPE_VERSION");
        var result=registry.execute(context(),request("GET_PROTOTYPE_VERSION","1",json.createObjectNode().put("prototypeVersionUuid","v1"),false));
        assertThat(result.status()).isEqualTo("SUCCEEDED");assertThat(result.summary().length()).isLessThanOrEqualTo(2048);assertThat(store.get(result.resultRef())).isNotEmpty();
        var replay=registry.execute(context(),request("GET_PROTOTYPE_VERSION","1",json.createObjectNode().put("prototypeVersionUuid","v1"),false));
        assertThat(replay.outputDigest()).isEqualTo(result.outputDigest());verify(gateway,times(1)).getPrototypeVersion(9L,"v1");
    }

    @Test void rejectsUnknownVersionExtraFieldsAndUnauthorizedResources(){
        DirectorReadModelGateway gateway=mock(DirectorReadModelGateway.class);var registry=registry(gateway,(u,p,n,a)->true,new InMemoryDirectorToolResultStore());
        assertThatThrownBy(()->registry.execute(context(),request("GET_PROTOTYPE_VERSION","2",json.createObjectNode().put("prototypeVersionUuid","v1"),false))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->registry.execute(context(),request("GET_PROTOTYPE_VERSION","1",json.createObjectNode().put("prototypeVersionUuid","v1").put("extra","injection"),false))).isInstanceOf(BusinessException.class);
        var forbidden=registry(gateway,(u,p,n,a)->false,new InMemoryDirectorToolResultStore());
        assertThatThrownBy(()->forbidden.execute(context(),request("GET_PROTOTYPE_VERSION","1",json.createObjectNode().put("prototypeVersionUuid","v1"),false))).isInstanceOf(BusinessException.class);
    }

    @Test void dryRunDoesNotInvokeAdapterAndTimeoutIsBounded(){
        DirectorReadModelGateway gateway=mock(DirectorReadModelGateway.class);var registry=registry(gateway,(u,p,n,a)->true,new InMemoryDirectorToolResultStore());
        assertThat(registry.execute(context(),request("GET_PLAYER_RUN_STATUS","1",json.createObjectNode().put("playerRunUuid","p1"),true)).status()).isEqualTo("DRY_RUN");verify(gateway,never()).getPlayerRunStatus(9L,"p1");
        DirectorTool slow=new DirectorTool(){public DirectorToolDefinition definition(){var schema=json.createObjectNode().put("type","object").put("additionalProperties",false);schema.putObject("properties");return new DirectorToolDefinition("SLOW_TOOL","1",schema,ToolPermission.READ,ToolRiskLevel.LOW,10,100,true);}public com.fasterxml.jackson.databind.JsonNode execute(DirectorToolContext c,com.fasterxml.jackson.databind.JsonNode a)throws Exception{Thread.sleep(100);return json.createObjectNode();}};
        var timed=new DefaultDirectorToolRegistry(java.util.List.of(slow),(u,p,n,a)->true,new InMemoryDirectorToolResultStore(),json,executor);
        assertThat(timed.execute(context(),request("SLOW_TOOL","1",json.createObjectNode(),false)).status()).isEqualTo("TIMED_OUT");
    }
    private DefaultDirectorToolRegistry registry(DirectorReadModelGateway gateway,DirectorResourceAuthorizer auth,DirectorToolResultStore store){return new DefaultDirectorToolRegistry(DefaultDirectorReadTools.create(gateway,json),auth,store,json,executor);}
    private DirectorToolContext context(){return new DirectorToolContext(7L,9L,"run","call");}
    private ToolCallRequest request(String name,String version,com.fasterxml.jackson.databind.JsonNode args,boolean dry){return new ToolCallRequest("call",name,version,"run:1",args,dry);}
}
