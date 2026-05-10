package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.model.RagExplainResponse;
import com.seewhy.syaiagent.rag.QueryRewriter;
import com.seewhy.syaiagent.rag.TravelDocumentLoader;
import com.seewhy.syaiagent.trace.AgentTraceService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TravelRagServiceTest {

    @Test
    void explainRagReturnsGracefulFallbackWhenVectorStoreUnavailable() {
        ChatClient chatClient = mock(ChatClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<VectorStore> vectorStoreProvider = mock(ObjectProvider.class);
        QueryRewriter queryRewriter = mock(QueryRewriter.class);
        AgentTraceService traceService = new AgentTraceService();
        when(vectorStoreProvider.getIfAvailable()).thenReturn(null);
        when(queryRewriter.doQueryRewrite("Japan travel tips")).thenReturn("rewritten Japan travel tips");
        TravelDocumentLoader documentLoader = mock(TravelDocumentLoader.class);
        when(documentLoader.loadMarkdowns()).thenReturn(java.util.List.of());

        TravelRagService service = new TravelRagService(chatClient, vectorStoreProvider, queryRewriter, traceService, documentLoader, "pgvector");

        RagExplainResponse response = service.explainRag("Japan travel tips", "rag-test");

        assertEquals("rag-test", response.chatId());
        assertEquals("lightweight-fallback", response.mode());
        assertEquals("Japan travel tips", response.originalQuery());
        assertEquals("rewritten Japan travel tips", response.rewrittenQuery());
        assertTrue(response.degraded());
        assertTrue(response.documents().isEmpty());
        assertTrue(traceService.getEvents("rag-test").size() >= 2);
    }

    @Test
    void chatWithRagFallsBackWhenPgVectorUnavailable() {
        ChatClient chatClient = mock(ChatClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<VectorStore> vectorStoreProvider = mock(ObjectProvider.class);
        QueryRewriter queryRewriter = mock(QueryRewriter.class);
        AgentTraceService traceService = new AgentTraceService();
        TravelDocumentLoader documentLoader = mock(TravelDocumentLoader.class);
        when(vectorStoreProvider.getIfAvailable()).thenReturn(null);
        when(documentLoader.loadMarkdowns()).thenReturn(java.util.List.of());

        TravelRagService service = new TravelRagService(chatClient, vectorStoreProvider, queryRewriter, traceService, documentLoader, "pgvector");

        String response = service.chatWithRag("Japan travel tips", "rag-chat-test");

        assertTrue(response.contains("Lightweight RAG"));
        assertTrue(traceService.getEvents("rag-chat-test").size() >= 2);
    }
}
