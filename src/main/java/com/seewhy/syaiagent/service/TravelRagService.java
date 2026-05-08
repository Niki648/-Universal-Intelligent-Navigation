package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.advisor.MyLoggerAdvisor;
import com.seewhy.syaiagent.rag.QueryRewriter;
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

    public TravelRagService(@Qualifier("travelChatClient") ChatClient chatClient,
                            ObjectProvider<VectorStore> vectorStoreProvider,
                            QueryRewriter queryRewriter) {
        this.chatClient = chatClient;
        this.vectorStoreProvider = vectorStoreProvider;
        this.queryRewriter = queryRewriter;
    }

    public String chatWithRag(String message, String chatId) {
        log.info("旅行知识库查询[{}]: {}", chatId, message);

        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            log.warn("RAG 向量库未启用，跳过知识库问答[{}]", chatId);
            return "当前未启用 RAG 向量库，请先配置并启用 VectorStore 后再使用知识库问答。";
        }

        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        log.debug("查询重写结果[{}]: {} -> {}", chatId, message, rewrittenMessage);

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .advisors(new QuestionAnswerAdvisor(vectorStore))
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("旅行知识库回复[{}]: {}", chatId, content);
        return content;
    }
}
