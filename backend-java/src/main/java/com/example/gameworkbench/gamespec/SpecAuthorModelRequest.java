package com.example.gameworkbench.gamespec;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record SpecAuthorModelRequest(long userId, String projectUuid, String idea,
        ObjectNode currentSpec, String diagnostics, int attempt) {}
