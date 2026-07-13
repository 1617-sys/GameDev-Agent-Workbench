package com.example.gameworkbench.service;
import java.util.*; import java.util.concurrent.*; import org.springframework.stereotype.Service;
@Service public class InMemoryVectorStore implements VectorStore { private final Map<String,Map<String,String>> metadata=new ConcurrentHashMap<>(); public void upsert(String id,float[] v,Map<String,String> m){metadata.put(id,Map.copyOf(m));} public void delete(String id){metadata.remove(id);} public Map<String,String> metadata(String id){return metadata.get(id);} }
