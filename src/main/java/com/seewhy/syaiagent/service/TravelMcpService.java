package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.advisor.MyLoggerAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TravelMcpService {

    private final ChatClient chatClient;
    private final ToolCallbackProvider toolCallbackProvider;

    public TravelMcpService(@Qualifier("travelChatClient") ChatClient chatClient,
                            ToolCallbackProvider toolCallbackProvider) {
        this.chatClient = chatClient;
        this.toolCallbackProvider = toolCallbackProvider;
    }

    public String chatWithMcp(String message, String chatId) {
        log.info("旅行MCP服务调用[{}]: {}", chatId, message);

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("旅行MCP服务回复[{}]: {}", chatId, content);
        return content;
    }
}
