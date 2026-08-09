package com.example.gameworkbench.dto.gamespec;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record CreateGenerationRunRequest(@NotNull JsonNode spec) {}
