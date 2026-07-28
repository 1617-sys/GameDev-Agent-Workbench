package com.example.gameworkbench.director.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.gameworkbench.dto.prototype.TunePrototypeVersionRequest;
import com.example.gameworkbench.experiment.PlayerExperimentService;
import com.example.gameworkbench.experiment.candidate.CandidateGenerationCommand;
import com.example.gameworkbench.experiment.candidate.DeterministicCandidateGenerator;
import com.example.gameworkbench.prototype.PrototypeDraftService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class ExperimentDirectorTools {
    private ExperimentDirectorTools(){}
    public static List<DirectorTool> create(PrototypeDraftService drafts,
            DeterministicCandidateGenerator generator,PlayerExperimentService experiments,ObjectMapper json){
        List<DirectorTool> tools=new ArrayList<>();
        tools.add(tool(def("CREATE_DRAFT_VERSION",ToolPermission.WRITE,draftSchema(json)),(c,a)->json.valueToTree(drafts.create(c.userId(),c.projectId(),c.runUuid(),a.path("parentVersionUuid").asText(),a.path("idempotencyKey").asText(),json.treeToValue(a.path("tuning"),TunePrototypeVersionRequest.class)))));
        tools.add(tool(def("REQUEST_HUMAN_APPROVAL",ToolPermission.WRITE,objectSchema(json,Map.of("prototypeVersionUuid",string(json)),List.of("prototypeVersionUuid"))),(c,a)->json.createObjectNode().put("status","WAITING_APPROVAL").put("approvalRef","approval://"+a.path("prototypeVersionUuid").asText())));
        tools.add(tool(def("GENERATE_NEIGHBOR_CANDIDATES",ToolPermission.WRITE,generatorSchema(json)),(c,a)->{Map<String,String>directions=new LinkedHashMap<>();Map<String,Integer>steps=new LinkedHashMap<>();for(JsonNode item:a.path("rules")){directions.put(item.path("parameter").asText(),item.path("direction").asText());steps.put(item.path("parameter").asText(),item.path("stepSize").asInt());}return json.valueToTree(generator.generate(c.userId(),c.projectId(),c.runUuid(),new CandidateGenerationCommand(a.path("parentVersionUuid").asText(),a.path("goalDigest").asText(),directions,steps,a.path("maxCandidates").asInt())));}));
        tools.add(tool(def("RUN_PLAYER_EXPERIMENT",ToolPermission.WRITE,runSchema(json)),(c,a)->experiments.run(c.userId(),c.projectId(),c.runUuid(),a)));
        tools.add(tool(def("GET_EXPERIMENT_STATUS",ToolPermission.READ,objectSchema(json,Map.of("experimentUuid",string(json)),List.of("experimentUuid"))),(c,a)->experiments.status(c.userId(),c.projectId(),c.runUuid(),a.path("experimentUuid").asText())));
        tools.add(tool(def("COMPARE_CANDIDATE_METRICS",ToolPermission.READ,compareSchema(json)),(c,a)->experiments.compare(c.userId(),c.projectId(),c.runUuid(),a)));
        return tools;
    }
    private static DirectorToolDefinition def(String name,ToolPermission permission,JsonNode schema){return new DirectorToolDefinition(name,"1",schema,permission,permission==ToolPermission.READ?ToolRiskLevel.LOW:ToolRiskLevel.MEDIUM,5000,2048,true);}
    private static ObjectNode draftSchema(ObjectMapper json){ObjectNode tuning=objectSchema(json,new LinkedHashMap<>(),List.of());for(String name:List.of("timeLimitSeconds","playerSpeed","playerMaxHealth","targetCollectibles","enemyCount"))((ObjectNode)tuning.path("properties")).set(name,integer(json,0,10000));return objectSchema(json,Map.of("parentVersionUuid",string(json),"idempotencyKey",string(json),"tuning",tuning),List.of("parentVersionUuid","idempotencyKey","tuning"));}
    private static ObjectNode generatorSchema(ObjectMapper json){ObjectNode rule=objectSchema(json,Map.of("parameter",enumString(json,"timeLimitSeconds","playerSpeed","playerMaxHealth","targetCollectibles","enemyCount"),"direction",enumString(json,"INCREASE","DECREASE"),"stepSize",integer(json,1,100)),List.of("parameter","direction","stepSize"));ObjectNode array=json.createObjectNode().put("type","array");array.set("items",rule);return objectSchema(json,Map.of("parentVersionUuid",string(json),"goalDigest",digest(json),"rules",array,"maxCandidates",integer(json,1,100)),List.of("parentVersionUuid","goalDigest","rules","maxCandidates"));}
    private static ObjectNode runSchema(ObjectMapper json){ObjectNode personas=json.createObjectNode().put("type","array");personas.set("items",enumString(json,"baseline-neutral","NOVICE","REGULAR","EXPERT"));ObjectNode seeds=json.createObjectNode().put("type","array");seeds.set("items",integer(json,0,Integer.MAX_VALUE));return objectSchema(json,Map.of("baselineVersionUuid",string(json),"candidateVersionUuid",string(json),"experimentKey",string(json),"personas",personas,"seeds",seeds,"maxSteps",integer(json,1,10000),"policyKind",enumString(json,"DETERMINISTIC","LLM")),List.of("baselineVersionUuid","candidateVersionUuid","experimentKey","personas","seeds","maxSteps","policyKind"));}
    private static ObjectNode compareSchema(ObjectMapper json){ObjectNode refs=json.createObjectNode().put("type","array");refs.set("items",string(json));return objectSchema(json,Map.of("baselineVersionUuid",string(json),"candidateVersionUuid",string(json),"baselineEpisodeUuids",refs,"candidateEpisodeUuids",refs.deepCopy(),"minimumSamples",integer(json,1,1000),"minimumCompletionRate",number(json,0,1),"maxExpertMeanTimeDelta",number(json,-1,10)),List.of("baselineVersionUuid","candidateVersionUuid","baselineEpisodeUuids","candidateEpisodeUuids","minimumSamples","minimumCompletionRate","maxExpertMeanTimeDelta"));}
    private static ObjectNode objectSchema(ObjectMapper json,Map<String,? extends JsonNode>properties,List<String>required){ObjectNode root=json.createObjectNode().put("type","object").put("additionalProperties",false);ObjectNode props=root.putObject("properties");properties.forEach(props::set);var req=root.putArray("required");required.forEach(req::add);return root;}
    private static ObjectNode string(ObjectMapper json){return json.createObjectNode().put("type","string").put("minLength",1).put("maxLength",128);}
    private static ObjectNode digest(ObjectMapper json){return string(json).put("pattern","^[0-9a-f]{64}$");}
    private static ObjectNode integer(ObjectMapper json,int min,int max){return json.createObjectNode().put("type","integer").put("minimum",min).put("maximum",max);}
    private static ObjectNode number(ObjectMapper json,double min,double max){return json.createObjectNode().put("type","number").put("minimum",min).put("maximum",max);}
    private static ObjectNode enumString(ObjectMapper json,String...values){ObjectNode node=string(json);var array=node.putArray("enum");for(String value:values)array.add(value);return node;}
    private static DirectorTool tool(DirectorToolDefinition definition,Operation operation){return new DirectorTool(){public DirectorToolDefinition definition(){return definition;}public JsonNode execute(DirectorToolContext context,JsonNode arguments)throws Exception{return operation.apply(context,arguments);}};}
    @FunctionalInterface private interface Operation{JsonNode apply(DirectorToolContext context,JsonNode args)throws Exception;}
}
