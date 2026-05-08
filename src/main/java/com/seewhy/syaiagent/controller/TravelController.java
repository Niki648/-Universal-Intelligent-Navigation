package com.seewhy.syaiagent.controller;

import com.seewhy.syaiagent.agent.SyManus;
import com.seewhy.syaiagent.app.TravelMaster;
import com.seewhy.syaiagent.model.ChatRequest;
import com.seewhy.syaiagent.model.ChatResponse;
import com.seewhy.syaiagent.model.HealthResponse;
import com.seewhy.syaiagent.model.QuickRequest;
import com.seewhy.syaiagent.model.TravelReport;
import com.seewhy.syaiagent.service.SseEmitterStreamService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController
@RequestMapping("/travel")
@Slf4j
public class TravelController {

    private final TravelMaster travelMaster;

    private final ToolCallback[] allTools;

    private final ChatModel chatModel;

    private final ChatMemory manusChatMemory;

    private final SseEmitterStreamService sseEmitterStreamService;

    public TravelController(TravelMaster travelMaster,
                            ToolCallback[] allTools,
                            @Qualifier("openAiChatModel") ChatModel chatModel,
                            @Qualifier("manusChatMemory") ChatMemory manusChatMemory,
                            SseEmitterStreamService sseEmitterStreamService) {
        this.travelMaster = travelMaster;
        this.allTools = allTools;
        this.chatModel = chatModel;
        this.manusChatMemory = manusChatMemory;
        this.sseEmitterStreamService = sseEmitterStreamService;
    }

    /**
     * 基础旅行对话
     */
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String chatId = normalizeChatId(request.getChatId());
        String response = travelMaster.doChat(request.getMessage(), chatId);
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
        return travelMaster.doChatByStream(message, id)
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
        return travelMaster.doChat(message, id);
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
        return travelMaster.doChatByStream(message, id)
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
        SyManus agent = new SyManus(allTools, chatModel);
        agent.setConversationChatId(id);
        agent.setConversationChatMemory(manusChatMemory);
        return agent.runStream(message);
    }

    /**
     * 生成旅行规划报告
     */
    @PostMapping("/report")
    public TravelReport generateReport(@Valid @RequestBody ChatRequest request) {
        String chatId = normalizeChatId(request.getChatId());
        return travelMaster.doChatWithReport(request.getMessage(), chatId);
    }

    /**
     * 旅行知识库问答
     */
    @PostMapping("/rag")
    public ChatResponse ragChat(@Valid @RequestBody ChatRequest request) {
        String chatId = normalizeChatId(request.getChatId());
        String response = travelMaster.doChatWithRag(request.getMessage(), chatId);
        return new ChatResponse(chatId, response);
    }

    /**
     * 快速旅行咨询
     */
    @PostMapping("/quick")
    public String quickConsult(@Valid @RequestBody QuickRequest request) {
        return travelMaster.quickTravelConsult(request.getMessage());
    }

    /**
     * 获取系统信息
     */
    @GetMapping("/system/info")
    public String getSystemInfo() {
        return travelMaster.getSystemInfo();
    }

    /**
     * 系统健康检查
     */
    @GetMapping("/health")
    public HealthResponse healthCheck() {
        return new HealthResponse("ok", "寰宇智导旅行规划系统运行正常");
    }

    private String generateChatId() {
        return "travel-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String normalizeChatId(String chatId) {
        return chatId != null && !chatId.isBlank() ? chatId : generateChatId();
    }

    private SseEmitter createChatEmitter(String message, String chatId, String logName) {
        String id = normalizeChatId(chatId);
        return sseEmitterStreamService.stream(id, logName, travelMaster.doChatByStream(message, id));
    }

    private void validateMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }
    }

}
