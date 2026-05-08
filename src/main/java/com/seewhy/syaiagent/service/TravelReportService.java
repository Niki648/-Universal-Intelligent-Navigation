package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.constant.TravelPromptConstant;
import com.seewhy.syaiagent.model.TravelReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TravelReportService {

    private final ChatClient chatClient;

    public TravelReportService(@Qualifier("travelChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public TravelReport generateReport(String message, String chatId) {
        log.info("生成旅行报告[{}]: {}", chatId, message);

        TravelReport travelReport = chatClient
                .prompt()
                .system(TravelPromptConstant.REPORT_PROMPT)
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(TravelReport.class);

        log.info("旅行报告生成完成[{}]: {}", chatId, travelReport.title());
        return travelReport;
    }
}
