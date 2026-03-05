package com.seewhy.syaiagent.controller;

import com.seewhy.syaiagent.agent.SyManus;
import com.seewhy.syaiagent.app.TravelMaster;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/travel")
@Slf4j
public class TravelController {

    @Autowired
    private TravelMaster travelMaster;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private SyManus syManusAgent;

    @Resource
    @Qualifier("manusChatMemory")
    private ChatMemory manusChatMemory;

    // track active SSE sessions by chatId to prevent duplicate concurrent processing
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    /**
     * 基础旅行对话
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String chatId = request.getChatId() != null ? request.getChatId() : generateChatId();
        String response = travelMaster.doChat(request.getMessage(), chatId);
        return new ChatResponse(chatId, response);
    }

    /**
     * 流式旅行对话
     */
    // @PostMapping("/chat/stream")
    // public Flux<String> streamChat(@RequestBody ChatRequest request) {
    //     String chatId = request.getChatId() != null ? request.getChatId() : generateChatId();
    //     return travelMaster.doChatByStream(request.getMessage(), chatId);
    // }

// ...

// 流式旅行对话（基于 Reactor Flux，直接作为 SSE 输出）
@GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamChat(@RequestParam String message,
                               @RequestParam(required = false) String chatId) {
    String id = chatId != null ? chatId : generateChatId();
    return travelMaster.doChatByStream(message, id)
            .doOnCancel(() -> log.info("SSE stream cancelled for {}", id))
            .onErrorContinue((err, obj) ->
                    log.debug("SSE stream error (ignored) for {}: {}", id, err.toString()));
}

    /**
     * 兼容 AiController 的同步 GET 接口（query 参数）
     */
    @GetMapping("/chat/sync")
    public String doChatSync(@RequestParam String message, @RequestParam(required = false) String chatId) {
        String id = chatId != null ? chatId : generateChatId();
        return travelMaster.doChat(message, id);
    }

    @GetMapping(value = "/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter doChatSse(@RequestParam String message, @RequestParam(required = false) String chatId) {
        String id = chatId != null ? chatId : generateChatId();
        SseEmitter sseEmitter = new SseEmitter(180000L);

        // prevent duplicate active sessions for same chatId
        SseEmitter existing = activeEmitters.putIfAbsent(id, sseEmitter);
        if (existing != null) {
            try {
                existing.send("[错误] 会话已在进行中，请稍后或换一个 chatId");
            } catch (Exception ignore) {
            }
            // return a short-lived emitter that immediately notifies and completes
            try {
                sseEmitter.send("[错误] 无法建立新的会话，已有活动会话");
            } catch (IOException ignore) {
            }
            sseEmitter.complete();
            return sseEmitter;
        }

        // Subscribe and keep Disposable so we can cancel the upstream when client disconnects
        var disposable = travelMaster.doChatByStream(message, id)
                .doOnCancel(() -> log.info("SSE sse cancelled for {}", id))
                .onErrorContinue((err, obj) -> log.debug("SSE sse error (ignored) for {}: {}", id, err.toString()))
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        log.debug("SSE send failed for {}: {}", id, e.getMessage());
                        try {
                            sseEmitter.complete();
                        } catch (Exception ignore) {
                        }
                    }
                }, err -> {
                    log.debug("SSE stream error for {}: {}", id, err.toString());
                    try {
                        sseEmitter.complete();
                    } catch (Exception ignore) {
                    }
                }, () -> {
                    try {
                        sseEmitter.complete();
                    } catch (Exception ignore) {
                    }
                });

        // Bind emitter lifecycle to subscription disposal and remove from active map
        sseEmitter.onCompletion(() -> {
            try {
                if (!disposable.isDisposed()) disposable.dispose();
            } catch (Exception ignore) {
            }
            activeEmitters.remove(id);
            log.info("SSE connection completed for {}", id);
        });
        sseEmitter.onTimeout(() -> {
            try {
                if (!disposable.isDisposed()) disposable.dispose();
            } catch (Exception ignore) {
            }
            activeEmitters.remove(id);
            try {
                sseEmitter.complete();
            } catch (Exception ignore) {
            }
            log.warn("SSE connection timeout for {}", id);
        });
        return sseEmitter;
    }

    @GetMapping(value = "/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatServerSentEvent(@RequestParam String message, @RequestParam(required = false) String chatId) {
        String id = chatId != null ? chatId : generateChatId();
        return travelMaster.doChatByStream(message, id)
            .doOnCancel(() -> log.info("SSE server_sent_event cancelled for {}", id))
            .onErrorContinue((err, obj) -> log.debug("SSE server_sent_event error (ignored) for {}: {}", id, err.toString()))
            .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build());
    }

    @GetMapping(value = "/chat/sse_emitter")
    public SseEmitter doChatSseEmitter(@RequestParam String message, @RequestParam(required = false) String chatId) {
        String id = chatId != null ? chatId : generateChatId();
        SseEmitter sseEmitter = new SseEmitter(180000L);

        // prevent duplicate active sessions for same chatId
        SseEmitter existing = activeEmitters.putIfAbsent(id, sseEmitter);
        if (existing != null) {
            try {
                sseEmitter.send("[错误] 无法建立新的会话，已有活动会话");
            } catch (IOException ignore) {
            }
            sseEmitter.complete();
            return sseEmitter;
        }

        var disposable = travelMaster.doChatByStream(message, id)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        log.debug("SSE send failed for {}: {}", id, e.getMessage());
                        try {
                            sseEmitter.complete();
                        } catch (Exception ignore) {
                        }
                    }
                }, err -> {
                    log.debug("SSE stream error for {}: {}", id, err.toString());
                    try {
                        sseEmitter.complete();
                    } catch (Exception ignore) {
                    }
                }, () -> {
                    try {
                        sseEmitter.complete();
                    } catch (Exception ignore) {
                    }
                });

        sseEmitter.onCompletion(() -> {
            try {
                if (!disposable.isDisposed()) disposable.dispose();
            } catch (Exception ignore) {
            }
            activeEmitters.remove(id);
            log.info("SSE emitter connection completed for {}", id);
        });
        sseEmitter.onTimeout(() -> {
            try {
                if (!disposable.isDisposed()) disposable.dispose();
            } catch (Exception ignore) {
            }
            activeEmitters.remove(id);
            try {
                sseEmitter.complete();
            } catch (Exception ignore) {
            }
            log.warn("SSE emitter timeout for {}", id);
        });
        return sseEmitter;
    }

    /**
     * 调用 Manus 智能体（流式）。支持 chatId 多轮对话：同一 chatId 会带上近期历史，便于解析「他/她」等指代。
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(@RequestParam String message,
                                      @RequestParam(required = false) String chatId) {
        String id = chatId != null && !chatId.isBlank() ? chatId : generateChatId();
        SyManus agent = new SyManus(allTools, dashscopeChatModel);
        agent.setConversationChatId(id);
        agent.setConversationChatMemory(manusChatMemory);
        return agent.runStream(message);
    }

    /**
     * 生成旅行规划报告
     */
    @PostMapping("/report")
    public TravelMaster.TravelReport generateReport(@RequestBody ChatRequest request) {
        String chatId = request.getChatId() != null ? request.getChatId() : generateChatId();
        return travelMaster.doChatWithReport(request.getMessage(), chatId);
    }

    /**
     * 旅行知识库问答
     */
    @PostMapping("/rag")
    public ChatResponse ragChat(@RequestBody ChatRequest request) {
        String chatId = request.getChatId() != null ? request.getChatId() : generateChatId();
        String response = travelMaster.doChatWithRag(request.getMessage(), chatId);
        return new ChatResponse(chatId, response);
    }

    /**
     * 快速旅行咨询
     */
    @PostMapping("/quick")
    public String quickConsult(@RequestBody QuickRequest request) {
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

    // 请求和响应对象
    public static class ChatRequest {
        private String message;
        private String chatId;

        // getters and setters
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getChatId() { return chatId; }
        public void setChatId(String chatId) { this.chatId = chatId; }
    }

    public static class QuickRequest {
        private String message;
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class ChatResponse {
        private String chatId;
        private String content;
        private long timestamp;

        public ChatResponse(String chatId, String content) {
            this.chatId = chatId;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }

        // getters
        public String getChatId() { return chatId; }
        public String getContent() { return content; }
        public long getTimestamp() { return timestamp; }
    }

    public static class HealthResponse {
        private String status;
        private String message;

        public HealthResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }

        // getters
        public String getStatus() { return status; }
        public String getMessage() { return message; }
    }
}