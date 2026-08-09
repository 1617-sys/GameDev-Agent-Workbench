package com.example.gameworkbench.gamespec;

@FunctionalInterface
public interface SpecAuthorModel {
    SpecAuthorModelResponse author(SpecAuthorModelRequest request);
}
