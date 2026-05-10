package com.seewhy.syaiagent.model;

public record RagRetrievedDocument(
        String title,
        String source,
        String snippet,
        Double score
) {
}
