package com.example.gameworkbench.service.impl;
import java.io.IOException; import java.nio.file.*; import java.util.UUID;
import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service;
import com.example.gameworkbench.service.KnowledgeStorage;
@Service public class LocalKnowledgeStorage implements KnowledgeStorage {
 private final Path root; public LocalKnowledgeStorage(@Value("${knowledge.storage-root:${java.io.tmpdir}/gamedev-knowledge}") String root) { this.root=Paths.get(root).toAbsolutePath().normalize(); }
 public String put(String suffix, byte[] content) throws IOException { Files.createDirectories(root); String key=UUID.randomUUID()+suffix; Path target=root.resolve(key).normalize(); if(!target.startsWith(root)) throw new SecurityException("invalid storage key"); Files.write(target,content,StandardOpenOption.CREATE_NEW); return key; }
 public byte[] read(String reference) throws IOException { Path target=root.resolve(reference).normalize(); if(!target.startsWith(root)||reference.contains("..")) throw new SecurityException("invalid storage reference"); return Files.readAllBytes(target); }
}
