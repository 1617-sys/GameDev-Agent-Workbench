package com.example.gameworkbench.common.enums;

import java.util.EnumSet;

public enum KnowledgeDocumentStatus {
    UPLOADED, PARSING, INDEXING, READY, INVALID, DELETED, FAILED;

    public boolean canTransitionTo(KnowledgeDocumentStatus target) {
        return switch (this) {
            case UPLOADED -> EnumSet.of(PARSING, INVALID, DELETED, FAILED).contains(target);
            case PARSING -> EnumSet.of(INDEXING, INVALID, DELETED, FAILED).contains(target);
            case INDEXING -> EnumSet.of(READY, INVALID, DELETED, FAILED).contains(target);
            case READY -> EnumSet.of(INVALID, DELETED).contains(target);
            case FAILED -> EnumSet.of(PARSING, INVALID, DELETED).contains(target);
            case INVALID -> EnumSet.of(DELETED).contains(target);
            case DELETED -> false;
        };
    }
}
