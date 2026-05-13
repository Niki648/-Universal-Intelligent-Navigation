package com.seewhy.syaiagent.model;

import java.time.Instant;

public record DemoArtifactResponse(
        String artifactId,
        String fileName,
        String mimeType,
        long size,
        String previewUrl,
        String downloadUrl,
        Instant expiresAt
) {
}
