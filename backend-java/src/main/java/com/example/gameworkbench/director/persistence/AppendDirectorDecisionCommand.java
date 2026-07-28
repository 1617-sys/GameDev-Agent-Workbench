package com.example.gameworkbench.director.persistence;

import com.example.gameworkbench.director.domain.DirectorDecisionKind;
import com.example.gameworkbench.director.domain.DirectorRunStatus;
import com.fasterxml.jackson.databind.JsonNode;

public record AppendDirectorDecisionCommand(long expectedStateVersion, int round, DirectorDecisionKind kind,
        String reasonSummary, String decisionDigest, JsonNode modelEvidence, JsonNode payload,
        DirectorRunStatus targetStatus, JsonNode checkpoint, String approvalRef, String errorCode) {}
