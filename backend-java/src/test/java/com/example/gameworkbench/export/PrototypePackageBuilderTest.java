package com.example.gameworkbench.export;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.export.FrozenPrototypeExport.FrozenArtifact;
import com.fasterxml.jackson.databind.*;

class PrototypePackageBuilderTest {
 private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 @Test void frozenInputBuildsByteIdenticalSafeOfflinePackageWithValidChecksums() throws Exception {
  PrototypePackageBuilder builder=new PrototypePackageBuilder(json);FrozenPrototypeExport input=fixture("Safe brief");
  byte[] first=builder.build(input,"a".repeat(64)),second=builder.build(input,"a".repeat(64));assertThat(first).isEqualTo(second);
  Map<String,byte[]> files=unzip(first);assertThat(files.keySet()).contains("demo/index.html","demo/runtime.js","demo/game-config.js","config/game-config.json","resources/manifest.json","playtest/summary.json","evaluation/balance-suggestion.json","manifest.json");
  assertThat(files.keySet()).allMatch(path->!path.startsWith("/")&&!path.contains("..")&&!path.contains("\\"));
  JsonNode manifest=json.readTree(files.get("manifest.json"));for(JsonNode item:manifest.path("files")){byte[] value=files.get(item.path("path").asText());assertThat(value).isNotNull();assertThat(item.path("sha256").asText()).isEqualTo(digest(value));}
  assertThat(new String(files.get("demo/index.html"),StandardCharsets.UTF_8)).doesNotContain("http://","https://");
 }
 @Test void sensitiveOrRemoteContentBlocksTheWholePackage(){PrototypePackageBuilder builder=new PrototypePackageBuilder(json);assertThatThrownBy(()->builder.build(fixture("api_key=secret"),"b".repeat(64))).isInstanceOf(BusinessException.class);}
 @Test void escapedNewlinesBackslashesAndOrdinaryTokenProseAreAllowed(){PrototypePackageBuilder builder=new PrototypePackageBuilder(json);assertThatCode(()->builder.build(fixture("A token budget note with an escaped \\n marker and ../ in prose"),"b".repeat(64))).doesNotThrowAnyException();}
 private FrozenPrototypeExport fixture(String brief)throws Exception{String config=Files.readString(Path.of("..","docs","requirements","v3","examples","game-config-2.0","valid-minimal.json"));String resource="{\"resources\":[{\"category\":\"player\",\"key\":\"player.blue\"}],\"runtimeCapabilityVersion\":\"arcade-collect-runtime/1\",\"schemaVersion\":\"1.0\",\"sourceArtifactUuid\":\"11111111-1111-1111-1111-111111111111\",\"sourceConfigDigest\":\""+"c".repeat(64)+"\"}";Map<String,FrozenArtifact> design=new TreeMap<>();design.put("gameConcept",new FrozenArtifact("c","d","Concept"));design.put("coreLoop",new FrozenArtifact("l","d","Loop"));design.put("tasks",new FrozenArtifact("t","d","Tasks"));return new FrozenPrototypeExport("1.0",1L,"project","Project",brief,"version",1,LocalDateTime.of(2026,1,2,3,4),"config","c".repeat(64),config,"manifest","m".repeat(64),resource,"arcade-collect-runtime/1","2026-01-02T03:04:00","{\"sampleSize\":5}","s".repeat(64),design,new FrozenArtifact("b","d","{\"recommendation\":\"Tune carefully\"}"));}
 private Map<String,byte[]> unzip(byte[] zip)throws Exception{Map<String,byte[]> result=new LinkedHashMap<>();try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(zip),StandardCharsets.UTF_8)){ZipEntry e;while((e=in.getNextEntry())!=null)result.put(e.getName(),in.readAllBytes());}return result;}
 private String digest(byte[] v)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v));}
}
