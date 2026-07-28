package com.example.gameworkbench.director.persistence;

import com.fasterxml.jackson.databind.JsonNode;

public record CreateDirectorRunCommand(String idempotencyKey, JsonNode goal, JsonNode budget, JsonNode checkpoint) {}
