package com.seewhy.syaiagent.controller;

import com.seewhy.syaiagent.service.WayfinderDemoService;
import com.seewhy.syaiagent.trace.AgentTraceEvent;
import com.seewhy.syaiagent.trace.AgentTraceService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/travel/trace")
public class TravelTraceController {

    private final AgentTraceService agentTraceService;
    private final WayfinderDemoService wayfinderDemoService;

    public TravelTraceController(AgentTraceService agentTraceService,
                                 WayfinderDemoService wayfinderDemoService) {
        this.agentTraceService = agentTraceService;
        this.wayfinderDemoService = wayfinderDemoService;
    }

    @GetMapping("/{chatId}")
    public List<AgentTraceEvent> getTraceEvents(@PathVariable String chatId) {
        if (wayfinderDemoService.isEnabled()) {
            List<AgentTraceEvent> events = agentTraceService.getEvents(chatId);
            return events.isEmpty() ? wayfinderDemoService.demoTrace(chatId) : events;
        }
        return agentTraceService.getEvents(chatId);
    }

    @GetMapping(value = "/{chatId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentTraceEvent>> streamTraceEvents(@PathVariable String chatId) {
        return agentTraceService.stream(chatId)
                .map(event -> ServerSentEvent.<AgentTraceEvent>builder()
                        .event(event.step().name())
                        .id(event.traceId())
                        .data(event)
                        .build());
    }
}
