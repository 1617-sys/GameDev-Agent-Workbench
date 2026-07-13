package com.example.gameworkbench.service;
import java.util.Map; public interface VectorStore { void upsert(String id,float[] vector,Map<String,String> metadata); void delete(String id); }
