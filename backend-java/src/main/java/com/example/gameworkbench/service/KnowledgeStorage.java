package com.example.gameworkbench.service;
import java.io.IOException;
public interface KnowledgeStorage { String put(String suffix, byte[] content) throws IOException; byte[] read(String reference) throws IOException; }
