package com.example.gameworkbench.gamespec;

import java.util.List;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record SpecAuthorResult(String status, ObjectNode spec, GameSpecCompilationResult compilation,
        List<SpecAuthorAttempt> attempts) {}
