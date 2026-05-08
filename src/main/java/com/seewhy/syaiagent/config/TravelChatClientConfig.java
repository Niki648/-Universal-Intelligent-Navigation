package com.seewhy.syaiagent.config;

import com.seewhy.syaiagent.advisor.MyLoggerAdvisor;
import com.seewhy.syaiagent.constant.TravelPromptConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class TravelChatClientConfig {

    @Bean
    @Qualifier("travelChatClient")
    public ChatClient travelChatClient(@Qualifier("openAiChatModel") ChatModel chatModel) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultSystem(TravelPromptConstant.SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor()
                )
                .build();

        log.info("寰宇智导旅行规划 ChatClient 已初始化完成");
        return chatClient;
    }
}
