package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.advisor.MyLoggerAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TravelToolService {

    private final ChatClient chatClient;
    private final ToolCallback[] travelTools;

    public TravelToolService(@Qualifier("travelChatClient") ChatClient chatClient,
                             ToolCallback[] travelTools) {
        this.chatClient = chatClient;
        this.travelTools = travelTools;
    }

    public String chatWithTools(String message, String chatId) {
        log.info("旅行工具调用[{}]: {}", chatId, message);

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(travelTools)
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("旅行工具回复[{}]: {}", chatId, content);
        return content;
    }
}
