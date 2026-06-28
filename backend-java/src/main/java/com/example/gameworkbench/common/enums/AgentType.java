package com.example.gameworkbench.common.enums;

public enum AgentType {
    REQUIREMENT_BREAKDOWN("/agent/requirement-breakdown", ArtifactType.REQUIREMENT_BREAKDOWN_RESULT),
    API_DESIGN("/agent/api-design", ArtifactType.API_DESIGN_RESULT),
    BUG_ANALYSIS("/agent/bug-analysis", ArtifactType.BUG_ANALYSIS_RESULT),
    PROMPT_GENERATE("/agent/prompt-generate", ArtifactType.PROMPT_GENERATE_RESULT),
    GAME_CONCEPT("/agent/game-concept", ArtifactType.GAME_CONCEPT_RESULT),
    CORE_LOOP_DESIGN("/agent/core-loop-design", ArtifactType.CORE_LOOP_DESIGN_RESULT),
    TASK_BREAKDOWN("/agent/task-breakdown", ArtifactType.TASK_BREAKDOWN_RESULT),
    GAME_CONFIG_GENERATE("/agent/game-config-generate", ArtifactType.GAME_CONFIG);

    private final String pythonPath;
    private final ArtifactType artifactType;

    AgentType(String pythonPath, ArtifactType artifactType) {
        this.pythonPath = pythonPath;
        this.artifactType = artifactType;
    }

    public String getPythonPath() {
        return pythonPath;
    }

    public ArtifactType getArtifactType() {
        return artifactType;
    }
}
