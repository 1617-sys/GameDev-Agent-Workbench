package com.example.gameworkbench.director.tool;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface DirectorResourceAuthorizer {
    boolean mayRead(long userId,long projectId,String toolName,JsonNode arguments);
}
