package com.enterprise.agentplatform.knowledge.service;

public record DocumentIndexingResult(boolean completed, String message) {

    public static DocumentIndexingResult success() {
        return new DocumentIndexingResult(true, null);
    }

    public static DocumentIndexingResult permanentFailure(String message) {
        return new DocumentIndexingResult(false, message);
    }
}
