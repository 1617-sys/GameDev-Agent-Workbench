package com.example.gameworkbench.experiment.candidate;

import java.util.Map;

public record CandidateGenerationCommand(String parentVersionUuid,String goalDigest,Map<String,String> directions,
        Map<String,Integer> stepSizes,int maxCandidates) {}
