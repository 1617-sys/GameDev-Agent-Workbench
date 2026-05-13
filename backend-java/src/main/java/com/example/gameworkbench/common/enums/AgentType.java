package com.example.gameworkbench.common.enums;

public enum AgentType {
    REQUIREMENT_BREAKDOWN("/agent/requirement-breakdown"),
    API_DESIGN("/agent/api-design"),
    BUG_ANALYSIS("/agent/bug-analysis"),
    PROMPT_GENERATE("/agent/prompt-generate");

    private final String pythonPath;

    AgentType(String pythonPath) {
        this.pythonPath = pythonPath;
    }

    public String getPythonPath() {
        return pythonPath;
    }
}
