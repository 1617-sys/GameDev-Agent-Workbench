package com.example.gameworkbench.service;
import java.util.List; public interface EmbeddingProvider { String model(); int dimension(); List<float[]> embed(List<String> input); }
