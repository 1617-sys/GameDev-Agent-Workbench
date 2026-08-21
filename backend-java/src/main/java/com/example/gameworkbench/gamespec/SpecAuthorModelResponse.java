package com.example.gameworkbench.gamespec;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record SpecAuthorModelResponse(ObjectNode spec, ObjectNode modelEvidence) {}
