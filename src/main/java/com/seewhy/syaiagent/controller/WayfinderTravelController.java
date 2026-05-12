package com.seewhy.syaiagent.controller;

import com.seewhy.syaiagent.agent.SyManus;
import com.seewhy.syaiagent.app.WayfinderTravelFacade;
import com.seewhy.syaiagent.model.ChatRequest;
import com.seewhy.syaiagent.model.ChatResponse;
import com.seewhy.syaiagent.model.DemoToolRequest;
import com.seewhy.syaiagent.model.DemoToolResponse;
import com.seewhy.syaiagent.model.DemoArtifactResponse;
import com.seewhy.syaiagent.model.HealthResponse;
import com.seewhy.syaiagent.model.QuickRequest;
import com.seewhy.syaiagent.model.RagExplainRequest;
import com.seewhy.syaiagent.model.RagExplainResponse;
import com.seewhy.syaiagent.model.TravelPlan;
import com.seewhy.syaiagent.model.TravelPlanRequest;
import com.seewhy.syaiagent.model.TravelReport;
import com.seewhy.syaiagent.service.DemoArtifactService;
import com.seewhy.syaiagent.service.SseEmitterStreamService;
import com.seewhy.syaiagent.service.SyManusArtifactLinkService;
import com.seewhy.syaiagent.service.SyManusDemoToolService;
import com.seewhy.syaiagent.service.TravelRagService;
import com.seewhy.syaiagent.service.WayfinderDemoService;
import com.seewhy.syaiagent.trace.AgentTraceEvent;
import com.seewhy.syaiagent.trace.AgentTraceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/travel")
@Slf4j
public class WayfinderTravelController {

    private final WayfinderTravelFacade wayfinderTravelFacade;

    private final ToolCallback[] allTools;

    private final ChatModel chatModel;

    private final ChatMemory manusChatMemory;

    private final SseEmitterStreamService sseEmitterStreamService;

    private final AgentTraceService agentTraceService;
    private final TravelRagService travelRagService;
    private final WayfinderDemoService wayfinderDemoService;
    private final DemoArtifactService demoArtifactService;
    private final SyManusDemoToolService syManusDemoToolService;
    private final SyManusArtifactLinkService syManusArtifactLinkService;
    private final ObjectMapper objectMapper;

    public WayfinderTravelController(WayfinderTravelFacade wayfinderTravelFacade,
                                     ToolCallback[] allTools,
                                     @Qualifier("openAiChatModel") ChatModel chatModel,
                                     @Qualifier("manusChatMemory") ChatMemory manusChatMemory,
                                     SseEmitterStreamService sseEmitterStreamService,
                                     AgentTraceService agentTraceService,
                                     TravelRagService travelRagService,
                                     WayfinderDemoService wayfinderDemoService,
                                     DemoArtifactService demoArtifactService,
                                     SyManusDemoToolService syManusDemoToolService,
                                     SyManusArtifactLinkService syManusArtifactLinkService,
                                     ObjectMapper objectMapper) {
        this.wayfinderTravelFacade = wayfinderTravelFacade;
        this.allTools = allTools;
        this.chatModel = chatModel;
        this.manusChatMemory = manusChatMemory;
        this.sseEmitterStreamService = sseEmitterStreamService;
        this.agentTraceService = agentTraceService;
        this.travelRagService = travelRagService;
        this.wayfinderDemoService = wayfinderDemoService;
        this.demoArtifactService = demoArtifactService;
        this.syManusDemoToolService = syManusDemoToolService;
        this.syManusArtifactLinkService = syManusArtifactLinkService;
        this.objectMapper = objectMapper;
    }

    /**
     * 基础旅行对话
     */
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String chatId = normalizeChatId(request.getChatId());
        String response = wayfinderTravelFacade.doChat(request.getMessage(), chatId);
        return new ChatResponse(chatId, response);
    }

    /**
     * 流式旅行对话（基于 Reactor Flux，直接作为 SSE 输出）
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam String message,
                                   @RequestParam(required = false) String chatId) {
        validateMessage(message);
        String id = normalizeChatId(chatId);
        if (wayfinderDemoService.isEnabled()) {
            return wayfinderDemoService.demoChatStream(message, id)
                    .doOnCancel(() -> log.info("Demo SSE stream cancelled for {}", id));
        }
        return wayfinderTravelFacade.doChatByStream(message, id)
                .doOnCancel(() -> log.info("SSE stream cancelled for {}", id))
                .onErrorContinue((err, obj) ->
                        log.debug("SSE stream error ignored for {}: {}", id, err.toString()));
    }

    /**
     * 兼容 AiController 的同步 GET 接口（query 参数）
     */
    @GetMapping("/chat/sync")
    public String doChatSync(@RequestParam String message, @RequestParam(required = false) String chatId) {
        validateMessage(message);
        String id = normalizeChatId(chatId);
        return wayfinderTravelFacade.doChat(message, id);
    }

    @GetMapping(value = "/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter doChatSse(@RequestParam String message, @RequestParam(required = false) String chatId) {
        validateMessage(message);
        return createChatEmitter(message, chatId, "chat/sse");
    }

    @GetMapping(value = "/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatServerSentEvent(@RequestParam String message, @RequestParam(required = false) String chatId) {
        validateMessage(message);
        String id = normalizeChatId(chatId);
        return wayfinderTravelFacade.doChatByStream(message, id)
            .doOnCancel(() -> log.info("SSE server_sent_event cancelled for {}", id))
            .onErrorContinue((err, obj) -> log.debug("SSE server_sent_event error ignored for {}: {}", id, err.toString()))
            .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build());
    }

    @GetMapping(value = "/chat/sse_emitter")
    public SseEmitter doChatSseEmitter(@RequestParam String message, @RequestParam(required = false) String chatId) {
        validateMessage(message);
        return createChatEmitter(message, chatId, "chat/sse_emitter");
    }

    /**
     * 调用 Manus 智能体（流式）。支持 chatId 多轮对话：同一 chatId 会带上近期历史，便于解析「他/她」等指代。
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(@RequestParam String message,
                                      @RequestParam(required = false) String chatId) {
        validateMessage(message);
        String id = normalizeChatId(chatId);
        SyManus agent = new SyManus(allTools, chatModel, syManusArtifactLinkService);
        agent.setConversationChatId(id);
        agent.setConversationChatMemory(manusChatMemory);
        agent.setArtifactMarkerFormatter(this::formatManusArtifactMarker);
        return agent.runStream(message);
    }

    @PostMapping("/manus/demo-tool")
    public DemoToolResponse runManusDemoTool(@RequestBody DemoToolRequest request) {
        if (request == null || request.type() == null || request.type().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Demo tool type is required.");
        }
        return syManusDemoToolService.runDemo(request.type());
    }

    @GetMapping("/manus/artifacts/{artifactId}")
    public ResponseEntity<Resource> previewManusArtifact(@PathVariable String artifactId) {
        return artifactResponse(artifactId, false);
    }

    @GetMapping("/manus/artifacts/{artifactId}/download")
    public ResponseEntity<Resource> downloadManusArtifact(@PathVariable String artifactId) {
        return artifactResponse(artifactId, true);
    }

    /**
     * 生成旅行规划报告
     */
    @PostMapping("/report")
    public TravelReport generateReport(@Valid @RequestBody ChatRequest request) {
        String chatId = normalizeChatId(request.getChatId());
        return wayfinderTravelFacade.doChatWithReport(request.getMessage(), chatId);
    }

    /**
     * 结构化旅行规划
     */
    @PostMapping("/plan")
    public TravelPlan generatePlan(@Valid @RequestBody TravelPlanRequest request) {
        String chatId = normalizeChatId(request.chatId());
        if (wayfinderDemoService.isEnabled()) {
            return wayfinderDemoService.demoTravelPlan();
        }
        return wayfinderTravelFacade.doStructuredPlan(request.message(), chatId);
    }

    /**
     * 旅行知识库问答
     */
    @PostMapping("/rag")
    public ChatResponse ragChat(@Valid @RequestBody ChatRequest request) {
        String chatId = normalizeChatId(request.getChatId());
        String response = wayfinderTravelFacade.doChatWithRag(request.getMessage(), chatId);
        return new ChatResponse(chatId, response);
    }

    @PostMapping("/rag/explain")
    public RagExplainResponse explainRag(@RequestBody RagExplainRequest request) {
        String chatId = normalizeChatId(request == null ? null : request.chatId());
        String message = request == null ? null : request.message();
        validateMessage(message);
        if (wayfinderDemoService.isEnabled()) {
            return wayfinderDemoService.demoRagExplain(message, chatId);
        }
        return travelRagService.explainRag(message, chatId);
    }

    /**
     * 快速旅行咨询
     */
    @PostMapping("/quick")
    public String quickConsult(@Valid @RequestBody QuickRequest request) {
        return wayfinderTravelFacade.quickTravelConsult(request.getMessage());
    }

    /**
     * 获取系统信息
     */
    @GetMapping("/system/info")
    public String getSystemInfo() {
        return wayfinderTravelFacade.getSystemInfo();
    }

    /**
     * 系统健康检查
     */
    @GetMapping("/health")
    public HealthResponse healthCheck() {
        return new HealthResponse("ok", "Wayfinder Travel Agent is healthy");
    }

    @GetMapping("/trace/{chatId}")
    public List<AgentTraceEvent> getTraceEvents(@PathVariable String chatId) {
        if (wayfinderDemoService.isEnabled()) {
            List<AgentTraceEvent> events = agentTraceService.getEvents(chatId);
            return events.isEmpty() ? wayfinderDemoService.demoTrace(chatId) : events;
        }
        return agentTraceService.getEvents(chatId);
    }

    @GetMapping(value = "/trace/{chatId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentTraceEvent>> streamTraceEvents(@PathVariable String chatId) {
        return agentTraceService.stream(chatId)
                .map(event -> ServerSentEvent.<AgentTraceEvent>builder()
                        .event(event.step().name())
                        .id(event.traceId())
                        .data(event)
                        .build());
    }

    private String generateChatId() {
        return "travel-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String normalizeChatId(String chatId) {
        return chatId != null && !chatId.isBlank() ? chatId : generateChatId();
    }

    private SseEmitter createChatEmitter(String message, String chatId, String logName) {
        String id = normalizeChatId(chatId);
        return sseEmitterStreamService.stream(id, logName, wayfinderTravelFacade.doChatByStream(message, id));
    }

    private ResponseEntity<Resource> artifactResponse(String artifactId, boolean attachment) {
        try {
            DemoArtifactService.ArtifactResource artifact = demoArtifactService.resolve(artifactId);
            ContentDisposition disposition = (attachment ? ContentDisposition.attachment() : ContentDisposition.inline())
                    .filename(artifact.fileName())
                    .build();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(artifact.mimeType()))
                    .contentLength(artifact.size())
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(artifact.resource());
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    private String formatManusArtifactMarker(DemoArtifactResponse artifact) {
        try {
            return "[ARTIFACT]" + objectMapper.writeValueAsString(artifact) + "[/ARTIFACT]";
        } catch (JsonProcessingException e) {
            log.debug("Could not serialize artifact marker for {}: {}", artifact.fileName(), e.getMessage());
            return "";
        }
    }

    private void validateMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }
    }

}
