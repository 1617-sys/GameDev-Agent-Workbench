package com.example.gameworkbench.gamespec;

import com.fasterxml.jackson.databind.node.ObjectNode;

@FunctionalInterface
public interface SpecAuthorModel {
    ObjectNode author(String idea, ObjectNode currentSpec, String diagnostics);
}
