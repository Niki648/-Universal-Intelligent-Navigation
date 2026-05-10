package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.advisor.MyLoggerAdvisor;
import com.seewhy.syaiagent.model.RagExplainResponse;
import com.seewhy.syaiagent.model.RagRetrievedDocument;
import com.seewhy.syaiagent.rag.QueryRewriter;
import com.seewhy.syaiagent.rag.TravelDocumentLoader;
import com.seewhy.syaiagent.trace.AgentTraceService;
import com.seewhy.syaiagent.trace.AgentTraceStatus;
import com.seewhy.syaiagent.trace.AgentTraceStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TravelRagService {

    private final ChatClient chatClient;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final QueryRewriter queryRewriter;
    private final AgentTraceService agentTraceService;
    private final TravelDocumentLoader documentLoader;
    private final String ragMode;

    public TravelRagService(@Qualifier("travelChatClient") ChatClient chatClient,
                            ObjectProvider<VectorStore> vectorStoreProvider,
                            QueryRewriter queryRewriter,
                            AgentTraceService agentTraceService,
                            TravelDocumentLoader documentLoader,
                            @Value("${travel.rag.mode:${rag.mode:demo}}") String ragMode) {
        this.chatClient = chatClient;
        this.vectorStoreProvider = vectorStoreProvider;
        this.queryRewriter = queryRewriter;
        this.agentTraceService = agentTraceService;
        this.documentLoader = documentLoader;
        this.ragMode = normalizeMode(ragMode);
    }

    public String chatWithRag(String message, String chatId) {
        log.info("Travel RAG query [{}] mode={}: {}", chatId, ragMode, message);
        agentTraceService.record(chatId, AgentTraceStep.RAG_RETRIEVAL, AgentTraceStatus.STARTED,
                "Preparing travel knowledge retrieval.", Map.of("mode", ragMode));

        if ("demo".equals(ragMode)) {
            return demoExplain(message, chatId).answer();
        }
        if ("lightweight".equals(ragMode)) {
            return lightweightExplain(message, chatId, false, null).answer();
        }

        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            String reason = "PgVector mode is enabled, but VectorStore is not available. Lightweight Markdown retrieval was used instead.";
            log.warn("{} [{}]", reason, chatId);
            agentTraceService.record(chatId, AgentTraceStep.RAG_RETRIEVAL, AgentTraceStatus.SKIPPED, reason);
            return lightweightExplain(message, chatId, true, reason).answer();
        }

        String rewrittenMessage = safeRewrite(message, chatId);
        agentTraceService.record(chatId, AgentTraceStep.RAG_RETRIEVAL, AgentTraceStatus.COMPLETED,
                "Knowledge retrieval query prepared.", Map.of("rewrittenQuery", rewrittenMessage));

        try {
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
        } catch (RuntimeException ex) {
            String reason = "PgVector RAG chat failed: " + ex.getClass().getSimpleName();
            log.warn("PgVector RAG chat failed [{}], falling back to lightweight retrieval", chatId, ex);
            agentTraceService.record(chatId, AgentTraceStep.RAG_RETRIEVAL, AgentTraceStatus.FAILED,
                    reason, Map.of("mode", "pgvector"));
            return lightweightExplain(message, chatId, true, reason).answer();
        }
    }

    public RagExplainResponse explainRag(String message, String chatId) {
        String normalizedChatId = chatId == null || chatId.isBlank() ? "rag-" + System.currentTimeMillis() : chatId.strip();
        String originalQuery = message == null ? "" : message.strip();
        agentTraceService.record(normalizedChatId, AgentTraceStep.RAG_RETRIEVAL, AgentTraceStatus.STARTED,
                "Starting explainable RAG retrieval.", Map.of("originalQuery", originalQuery, "mode", ragMode));

        if ("demo".equals(ragMode)) {
            return demoExplain(originalQuery, normalizedChatId);
        }
        if ("lightweight".equals(ragMode)) {
            return lightweightExplain(originalQuery, normalizedChatId, false, null);
        }

        String rewrittenQuery = safeRewrite(originalQuery, normalizedChatId);
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            String reason = "PgVector mode is enabled, but VectorStore is not available. Lightweight Markdown retrieval was used instead.";
            log.warn("{} [{}]", reason, normalizedChatId);
            agentTraceService.record(normalizedChatId, AgentTraceStep.RAG_RETRIEVAL, AgentTraceStatus.SKIPPED,
                    reason, Map.of("rewrittenQuery", rewrittenQuery));
            return lightweightExplainWithQuery(originalQuery, rewrittenQuery, normalizedChatId, true, reason);
        }

        List<RagRetrievedDocument> documents;
        try {
            List<Document> retrieved = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(rewrittenQuery)
                    .topK(4)
                    .similarityThresholdAll()
                    .build());
            documents = retrieved == null ? List.of() : retrieved.stream()
                    .filter(Objects::nonNull)
                    .map(this::toRetrievedDocument)
                    .toList();
            agentTraceService.record(normalizedChatId, AgentTraceStep.RAG_RETRIEVAL, AgentTraceStatus.COMPLETED,
                    "Retrieved documents for explainable RAG.",
                    Map.of("rewrittenQuery", rewrittenQuery, "documentCount", documents.size(), "mode", "pgvector"));
        } catch (RuntimeException ex) {
            String reason = "PgVector retrieval failed: " + ex.getClass().getSimpleName();
            log.warn("Explainable PgVector RAG retrieval failed [{}]", normalizedChatId, ex);
            agentTraceService.record(normalizedChatId, AgentTraceStep.RAG_RETRIEVAL, AgentTraceStatus.FAILED,
                    reason, Map.of("rewrittenQuery", rewrittenQuery));
            return lightweightExplainWithQuery(originalQuery, rewrittenQuery, normalizedChatId, true, reason);
        }

        String answer = generateExplainableAnswer(rewrittenQuery, documents, normalizedChatId);
        return new RagExplainResponse(normalizedChatId, "pgvector", originalQuery, rewrittenQuery, documents, answer, false, null);
    }

    private RagExplainResponse demoExplain(String originalQuery, String chatId) {
        String query = originalQuery == null || originalQuery.isBlank()
                ? "What should I consider for a relaxed family trip to Japan?"
                : originalQuery.strip();
        agentTraceService.record(chatId, AgentTraceStep.RAG_RETRIEVAL, AgentTraceStatus.COMPLETED,
                "Returned stable demo RAG explain response.", Map.of("mode", "demo"));
        return new RagExplainResponse(
                chatId,
                "demo",
                query,
                "relaxed family travel budget risks local Markdown knowledge",
                List.of(
                        new RagRetrievedDocument("Demo travel guide", "demo://travel-guide",
                                "Public demo mode returns a stable grounded-looking response without calling PgVector or a cloud database.", 0.9),
                        new RagRetrievedDocument("Demo budget tips", "demo://budget-tips",
                                "For cost-controlled travel planning, keep accommodation, food, transport, and uncertainty visible in the answer.", 0.84)
                ),
                "This public demo uses a fixed RAG response to avoid database cost. For a relaxed family trip, keep pacing light, stay near transit, budget by category, and verify live prices, weather, visa rules, and opening hours before booking.",
                false,
                null
        );
    }

    private RagExplainResponse lightweightExplain(String originalQuery, String chatId, boolean degraded, String reason) {
        String query = originalQuery == null ? "" : originalQuery.strip();
        return lightweightExplainWithQuery(query, simpleRewrite(query), chatId, degraded, reason);
    }

    private RagExplainResponse lightweightExplainWithQuery(String originalQuery, String rewrittenQuery, String chatId, boolean degraded, String reason) {
        List<RagRetrievedDocument> documents = retrieveFromMarkdown(rewrittenQuery);
        agentTraceService.record(chatId, AgentTraceStep.RAG_RETRIEVAL, AgentTraceStatus.COMPLETED,
                "Lightweight Markdown retrieval completed.",
                Map.of("mode", "lightweight", "documentCount", documents.size()));
        return new RagExplainResponse(
                chatId,
                degraded ? "lightweight-fallback" : "lightweight",
                originalQuery,
                rewrittenQuery,
                documents,
                lightweightAnswer(originalQuery, documents),
                degraded,
                reason
        );
    }

    private List<RagRetrievedDocument> retrieveFromMarkdown(String query) {
        List<Document> documents = documentLoader.loadMarkdowns();
        if (documents.isEmpty()) {
            return List.of();
        }
        Set<String> terms = tokenize(query);
        return documents.stream()
                .filter(Objects::nonNull)
                .map(document -> Map.entry(document, lightweightScore(document.getText(), terms)))
                .filter(entry -> entry.getValue() > 0 || terms.isEmpty())
                .sorted(Map.Entry.<Document, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(4)
                .map(entry -> toLightweightDocument(entry.getKey(), entry.getValue()))
                .toList();
    }

    private RagRetrievedDocument toLightweightDocument(Document document, double score) {
        Map<String, Object> metadata = document.getMetadata() == null ? Map.of() : document.getMetadata();
        String filename = firstMetadata(metadata, "filename", "source", "file", "path");
        String title = filename == null ? "Local Markdown travel note" : filename;
        return new RagRetrievedDocument(title, "classpath:document/" + title, snippet(document.getText()), score);
    }

    private double lightweightScore(String text, Set<String> terms) {
        if (terms.isEmpty()) {
            return 0.1;
        }
        String haystack = text == null ? "" : text.toLowerCase();
        long matches = terms.stream().filter(haystack::contains).count();
        return matches == 0 ? 0 : Math.min(1.0, (double) matches / terms.size());
    }

    private Set<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(query.toLowerCase().split("[^\\p{IsAlphabetic}\\p{IsDigit}]+"))
                .map(String::strip)
                .filter(token -> token.length() >= 2)
                .collect(Collectors.toSet());
    }

    private String simpleRewrite(String query) {
        if (query == null || query.isBlank()) {
            return "travel budget itinerary risk";
        }
        return query.strip();
    }

    private String safeRewrite(String originalQuery, String chatId) {
        try {
            String rewritten = queryRewriter.doQueryRewrite(originalQuery);
            String result = rewritten == null || rewritten.isBlank() ? originalQuery : rewritten.strip();
            agentTraceService.record(chatId, AgentTraceStep.RAG_RETRIEVAL, AgentTraceStatus.COMPLETED,
                    "Query rewrite completed.", Map.of("rewrittenQuery", result));
            return result;
        } catch (RuntimeException ex) {
            log.warn("RAG query rewrite failed [{}], using original query", chatId, ex);
            agentTraceService.record(chatId, AgentTraceStep.RAG_RETRIEVAL, AgentTraceStatus.SKIPPED,
                    "Query rewrite unavailable, using original query.", Map.of("error", ex.getClass().getSimpleName()));
            return originalQuery;
        }
    }

    private RagRetrievedDocument toRetrievedDocument(Document document) {
        Map<String, Object> metadata = document.getMetadata() == null ? Map.of() : document.getMetadata();
        String title = firstMetadata(metadata, "title", "filename", "file_name", "name");
        String source = firstMetadata(metadata, "source", "file", "path", "url");
        return new RagRetrievedDocument(
                title == null ? "Travel knowledge note" : title,
                source == null ? "local knowledge base" : source,
                snippet(document.getText()),
                document.getScore()
        );
    }

    private String firstMetadata(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private String snippet(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").strip();
        return normalized.length() <= 360 ? normalized : normalized.substring(0, 360) + "...";
    }

    private String lightweightAnswer(String query, List<RagRetrievedDocument> documents) {
        if (documents.isEmpty()) {
            return "Lightweight RAG is enabled, but no local Markdown snippets matched this question. The public site avoids PgVector cost by default; try a travel budget, destination, itinerary, or risk question.";
        }
        String sources = documents.stream()
                .map(RagRetrievedDocument::source)
                .distinct()
                .collect(Collectors.joining(", "));
        return "Lightweight RAG searched local Markdown only, so it can answer without PgVector cost. For \"%s\", review the snippets below and treat live prices, weather, visa rules, and opening hours as information that still needs current verification. Sources: %s."
                .formatted(query, sources);
    }

    private String generateExplainableAnswer(String query, List<RagRetrievedDocument> documents, String chatId) {
        String context = documents.isEmpty()
                ? "No retrieved documents."
                : documents.stream()
                .map(document -> "- %s (%s): %s".formatted(document.title(), document.source(), document.snippet()))
                .reduce("", (left, right) -> left + right + System.lineSeparator());
        try {
            return chatClient.prompt()
                    .system("""
                            You are a travel knowledge assistant.
                            Answer using the retrieved context when it is relevant.
                            Be concise, practical, and explicit about uncertainty.
                            """)
                    .user("""
                            User question:
                            %s

                            Retrieved context:
                            %s
                            """.formatted(query, context))
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    .call()
                    .content();
        } catch (RuntimeException ex) {
            log.warn("Explainable RAG answer generation failed [{}]", chatId, ex);
            agentTraceService.record(chatId, AgentTraceStep.RAG_RETRIEVAL, AgentTraceStatus.FAILED,
                    "Answer generation failed after retrieval.", Map.of("error", ex.getClass().getSimpleName()));
            return "Documents were retrieved, but answer generation is unavailable right now. Review the retrieved snippets below.";
        }
    }

    private String normalizeMode(String mode) {
        String normalized = mode == null ? "demo" : mode.strip().toLowerCase();
        if (normalized.equals("demo") || normalized.equals("lightweight") || normalized.equals("pgvector")) {
            return normalized;
        }
        log.warn("Unknown travel.rag.mode='{}', falling back to demo", mode);
        return "demo";
    }
}
