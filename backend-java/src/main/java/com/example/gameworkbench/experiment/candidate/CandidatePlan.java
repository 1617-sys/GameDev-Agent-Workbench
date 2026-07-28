package com.example.gameworkbench.experiment.candidate;

import java.util.List;

public record CandidatePlan(String generatorVersion,String inputDigest,String planDigest,List<Item> candidates,String status){
    public record Item(int ordinal,String candidateUuid,String prototypeVersionUuid,String configDigest,Object tuning){}
}
