package com.example.gameworkbench.service; public record RetrievalRequest(Long projectId,String query,int topK,float minScore,int maxChars) { }
