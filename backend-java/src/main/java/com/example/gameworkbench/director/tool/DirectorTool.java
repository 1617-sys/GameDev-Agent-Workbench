package com.example.gameworkbench.director.tool;

import com.fasterxml.jackson.databind.JsonNode;

public interface DirectorTool {
    DirectorToolDefinition definition();
    JsonNode execute(DirectorToolContext context,JsonNode arguments) throws Exception;
}
