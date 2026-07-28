package com.example.gameworkbench.director.persistence;

import java.util.List;
import com.example.gameworkbench.entity.DirectorDecisionRecord;
import com.example.gameworkbench.entity.DirectorRun;
import com.example.gameworkbench.entity.DirectorToolCallRecord;
import com.example.gameworkbench.entity.ExperimentCandidate;

public record DirectorRunView(DirectorRun run, List<DirectorDecisionRecord> decisions,
        List<DirectorToolCallRecord> toolCalls, List<ExperimentCandidate> candidates) {}
