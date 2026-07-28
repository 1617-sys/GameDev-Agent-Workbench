package com.example.gameworkbench.director.client;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface DirectorDecisionClient { JsonNode decide(JsonNode snapshot,String traceId); }
