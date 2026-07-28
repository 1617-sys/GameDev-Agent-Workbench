package com.example.gameworkbench.director.tool;

import java.util.List;
import java.util.function.BiFunction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class DefaultDirectorReadTools {
    private DefaultDirectorReadTools(){}
    public static List<DirectorTool> create(DirectorReadModelGateway gateway,ObjectMapper json){
        return List.of(
            tool(def(json,"GET_PROTOTYPE_VERSION",schema(json,"prototypeVersionUuid")),(c,a)->gateway.getPrototypeVersion(c.projectId(),a.path("prototypeVersionUuid").asText())),
            tool(def(json,"GET_MACHINE_EPISODE_METRICS",schema(json,"prototypeVersionUuid")),(c,a)->gateway.getMachineEpisodeMetrics(c.projectId(),a.path("prototypeVersionUuid").asText())),
            tool(def(json,"GET_PLAYER_RUN_STATUS",schema(json,"playerRunUuid")),(c,a)->gateway.getPlayerRunStatus(c.projectId(),a.path("playerRunUuid").asText())),
            tool(def(json,"COMPARE_PROTOTYPE_CONFIGS",schema(json,"baselineVersionUuid","candidateVersionUuid")),(c,a)->gateway.comparePrototypeConfigs(c.projectId(),a.path("baselineVersionUuid").asText(),a.path("candidateVersionUuid").asText())));
    }
    private static DirectorToolDefinition def(ObjectMapper json,String name,JsonNode schema){return new DirectorToolDefinition(name,"1",schema,ToolPermission.READ,ToolRiskLevel.LOW,2000,2048,true);}
    private static ObjectNode schema(ObjectMapper json,String... names){ObjectNode root=json.createObjectNode();root.put("type","object").put("additionalProperties",false);ObjectNode props=root.putObject("properties");var required=root.putArray("required");for(String name:names){props.putObject(name).put("type","string").put("minLength",1).put("maxLength",80);required.add(name);}return root;}
    private static DirectorTool tool(DirectorToolDefinition definition,BiFunction<DirectorToolContext,JsonNode,JsonNode> operation){return new DirectorTool(){public DirectorToolDefinition definition(){return definition;}public JsonNode execute(DirectorToolContext context,JsonNode arguments){return operation.apply(context,arguments);}};}
}
