package com.example.gameworkbench.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.entity.KnowledgeChunk;
import com.example.gameworkbench.entity.KnowledgeDocument;
import com.example.gameworkbench.mapper.KnowledgeChunkMapper;
import lombok.RequiredArgsConstructor;

/** Worker-only indexing operation; it never issues retrieval queries. */
@Service @RequiredArgsConstructor
public class KnowledgeIndexingService {
 private final KnowledgeChunker chunker; private final EmbeddingProvider embeddingProvider; private final VectorStore vectorStore; private final KnowledgeChunkMapper chunks; private final KnowledgeStorage storage;
 public void index(KnowledgeDocument document, String extractedText) {
  if (document.getDeleted()!=null && document.getDeleted()!=0) return;
  if (chunks.selectCount(new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getDocumentId,document.getId()).eq(KnowledgeChunk::getChunkingVersion,KnowledgeChunker.VERSION).eq(KnowledgeChunk::getEmbeddingModel,embeddingProvider.model()))>0) return;
  List<String> texts=chunker.chunk(extractedText); if(texts.isEmpty()) throw new IllegalArgumentException("empty extracted text");
  List<float[]> vectors=embeddingProvider.embed(texts); if(vectors.size()!=texts.size()) throw new IllegalStateException("embedding batch incomplete");
  for(int i=0;i<texts.size();i++){ try { String uuid=UUID.randomUUID().toString(); String ref="knowledge/"+document.getProjectId()+"/"+uuid; String textRef=storage.put(".chunk.txt",texts.get(i).getBytes(StandardCharsets.UTF_8)); Map<String,String> meta=Map.of("projectId",String.valueOf(document.getProjectId()),"documentUuid",document.getDocumentUuid(),"documentVersion",String.valueOf(document.getVersion()),"chunkUuid",uuid,"status","ACTIVE","embeddingModel",embeddingProvider.model(),"chunkingVersion",KnowledgeChunker.VERSION,"textRef",textRef,"dimension",String.valueOf(embeddingProvider.dimension())); vectorStore.upsert(ref,vectors.get(i),meta); KnowledgeChunk c=new KnowledgeChunk(); c.setChunkUuid(uuid); c.setDocumentId(document.getId()); c.setProjectId(document.getProjectId()); c.setOrdinal(i); c.setTextRef(textRef); c.setTextHash(hash(texts.get(i))); c.setTokenCount((texts.get(i).length()+3)/4); c.setChunkingVersion(KnowledgeChunker.VERSION); c.setEmbeddingModel(embeddingProvider.model()); c.setEmbeddingDimension(embeddingProvider.dimension()); c.setIndexStatus("INDEXED"); c.setVectorRef(ref); chunks.insert(c); } catch(java.io.IOException e){ throw new IllegalStateException("chunk storage failed",e); } }
 }
 public void invalidate(KnowledgeDocument document){ for(KnowledgeChunk c:chunks.selectList(new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getDocumentId,document.getId()))){ if(c.getVectorRef()!=null) vectorStore.delete(c.getVectorRef()); c.setIndexStatus("INVALID"); chunks.updateById(c); } }
 private static String hash(String s){try{byte[] d=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)); StringBuilder b=new StringBuilder(); for(byte x:d)b.append(String.format("%02x",x)); return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}
