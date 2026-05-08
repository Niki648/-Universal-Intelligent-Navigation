package com.seewhy.syaiagent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@Slf4j
public class TravelChatService {

    private final ChatClient chatClient;

    public TravelChatService(@Qualifier("travelChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String chat(String message, String chatId) {
        log.info("用户[{}]提问: {}", chatId, message);

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("AI回复[{}]: {}", chatId, content);
        return content;
    }

    public Flux<String> streamChat(String message, String chatId) {
        log.info("用户[{}]提问（流式）: {}", chatId, message);

        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content()
                .doOnNext(chunk -> log.debug("流式回复[{}]: {}", chatId, chunk));
    }
}
