package com.seewhy.syaiagent.model;

public record WayfinderDemoStatusResponse(
        boolean demoMode,
        boolean liveManusAvailable,
        boolean searchAvailable,
        boolean imageSearchAvailable
) {
}
