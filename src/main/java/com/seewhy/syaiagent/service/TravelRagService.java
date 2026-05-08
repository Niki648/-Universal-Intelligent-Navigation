package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.advisor.MyLoggerAdvisor;
import com.seewhy.syaiagent.rag.QueryRewriter;
import com.seewhy.syaiagent.trace.AgentTraceService;
import com.seewhy.syaiagent.trace.AgentTraceStatus;
import com.seewhy.syaiagent.trace.AgentTraceStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TravelRagService {

    private final ChatClient chatClient;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final QueryRewriter queryRewriter;
    private final AgentTraceService agentTraceService;

    public TravelRagService(@Qualifier("travelChatClient") ChatClient chatClient,
                            ObjectProvider<VectorStore> vectorStoreProvider,
                            QueryRewriter queryRewriter,
                            AgentTraceService agentTraceService) {
        this.chatClient = chatClient;
        this.vectorStoreProvider = vectorStoreProvider;
        this.queryRewriter = queryRewriter;
        this.agentTraceService = agentTraceService;
    }

    public String chatWithRag(String message, String chatId) {
        log.info("Travel RAG query [{}]: {}", chatId, message);
        agentTraceService.record(chatId, AgentTraceStep.RAG_RETRIEVAL, AgentTraceStatus.STARTED, "Preparing travel knowledge retrieval.");

        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            log.warn("VectorStore is unavailable, skipping RAG [{}]", chatId);
            agentTraceService.record(chatId, AgentTraceStep.RAG_RETRIEVAL, AgentTraceStatus.SKIPPED, "VectorStore is not available.");
            return "当前未启用 RAG 向量库，请先配置并启用 VectorStore 后再使用知识库问答。";
        }

        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        log.debug("RAG rewritten query [{}]: {} -> {}", chatId, message, rewrittenMessage);
        agentTraceService.record(chatId, AgentTraceStep.RAG_RETRIEVAL, AgentTraceStatus.COMPLETED, "Knowledge retrieval query prepared.");

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .advisors(new QuestionAnswerAdvisor(vectorStore))
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("Travel RAG response [{}]: {}", chatId, content);
        return content;
    }
}
