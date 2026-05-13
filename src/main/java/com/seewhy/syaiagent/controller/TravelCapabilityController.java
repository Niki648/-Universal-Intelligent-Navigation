package com.seewhy.syaiagent.controller;

import com.seewhy.syaiagent.model.WayfinderDemoStatusResponse;
import com.seewhy.syaiagent.service.CapabilityStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/travel")
public class TravelCapabilityController {

    private final CapabilityStatusService capabilityStatusService;

    public TravelCapabilityController(CapabilityStatusService capabilityStatusService) {
        this.capabilityStatusService = capabilityStatusService;
    }

    @GetMapping("/demo-status")
    public WayfinderDemoStatusResponse demoStatus() {
        return capabilityStatusService.currentStatus();
    }
}
