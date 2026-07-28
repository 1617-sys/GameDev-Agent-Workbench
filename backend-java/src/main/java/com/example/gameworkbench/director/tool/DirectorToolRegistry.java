package com.example.gameworkbench.director.tool;

import java.util.List;

public interface DirectorToolRegistry {
    List<DirectorToolDefinition> discover();
    ToolCallResult execute(DirectorToolContext context,ToolCallRequest request);
}
