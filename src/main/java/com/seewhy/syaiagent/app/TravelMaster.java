package com.seewhy.syaiagent.app;

import com.seewhy.syaiagent.advisor.MyLoggerAdvisor;
import com.seewhy.syaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@Slf4j
public class TravelMaster {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = "你是一名专业的旅行规划专家，名叫寰宇智导。开场时热情问候用户，表明可以帮助规划旅行。" +
            "采用分阶段引导方式收集信息：第一阶段询问基础信息（目的地、时间、预算、同行人员）；第二阶段了解兴趣偏好（自然风光、历史文化、美食购物等）；第三阶段挖掘个性化需求（过往经历、特殊要求）。" +
            "每次回答后至少提出1-2个引导性问题，使用开放式提问和选项引导技巧。当信息足够时，提供结构化建议：1)总结用户需求 2)推荐2-3个行程方向 3)详细行程安排 4)预算分配 5)实用贴士 6)备选方案。" +
            "对话中保持温暖专业的语气，适当使用表情符号，关注用户的情绪变化，提供贴心的旅行建议。";

    /**
     * 初始化 ChatClient
     *
     * @param dashscopeChatModel
     */
    public TravelMaster(ChatModel dashscopeChatModel) {
        // 初始化基于内存的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // 自定义日志 Advisor，可按需开启
                        new MyLoggerAdvisor()
                )
                .build();

        log.info("寰宇智导旅行规划系统已初始化完成");
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     *
     * @param message 用户消息
     * @param chatId 会话ID
     * @return AI回复内容
     */
    public String doChat(String message, String chatId) {
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

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     *
     * @param message 用户消息
     * @param chatId 会话ID
     * @return 流式响应内容
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        log.info("用户[{}]提问（流式）: {}", chatId, message);

        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content()
                .doOnNext(chunk -> log.debug("流式回复[{}]: {}", chatId, chunk));
    }

    /**
     * 旅行规划报告记录类
     */
    public record TravelReport(String title, List<String> recommendations,
                               String destination, String duration,
                               String budget, List<String> itinerary) {
        public TravelReport {
            if (recommendations == null) {
                recommendations = List.of();
            }
            if (itinerary == null) {
                itinerary = List.of();
            }
        }
    }

    /**
     * AI 旅行报告功能（实战结构化输出）
     *
     * @param message 用户消息
     * @param chatId 会话ID
     * @return 旅行规划报告
     */
    public TravelReport doChatWithReport(String message, String chatId) {
        log.info("生成旅行报告[{}]: {}", chatId, message);

        String reportPrompt = SYSTEM_PROMPT + "\n请根据对话内容生成旅行规划报告，报告包含：标题、主要建议、目的地、旅行时长、预算概览和行程框架。";

        TravelReport travelReport = chatClient
                .prompt()
                .system(reportPrompt)
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(TravelReport.class);

        log.info("旅行报告生成完成[{}]: {}", chatId, travelReport.title());
        return travelReport;
    }

    // 旅行知识库问答功能

    @Resource
    private VectorStore travelVectorStore; // 修改为旅行向量存储

    @Resource
    private Advisor travelRagCloudAdvisor; // 修改为旅行RAG云顾问

    @Resource
    private VectorStore pgVectorVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    /**
     * 和 RAG 知识库进行对话（旅行知识）
     *
     * @param message 用户消息
     * @param chatId 会话ID
     * @return AI回复内容
     */
    public String doChatWithRag(String message, String chatId) {
        log.info("旅行知识库查询[{}]: {}", chatId, message);

        // 查询重写
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        log.debug("查询重写结果[{}]: {} -> {}", chatId, message, rewrittenMessage);

        ChatResponse chatResponse = chatClient
                .prompt()
                // 使用改写后的查询
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                // 应用 RAG 知识库问答（旅行知识库）
                .advisors(new QuestionAnswerAdvisor(travelVectorStore))
                // 应用 RAG 检索增强服务（基于云知识库服务）
//                .advisors(travelRagCloudAdvisor)
                // 应用 RAG 检索增强服务（基于 PgVector 向量存储）
//                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("旅行知识库回复[{}]: {}", chatId, content);
        return content;
    }

    // AI 调用工具能力
    @Resource
    private ToolCallback[] travelTools; // 修改为旅行工具

    /**
     * AI 旅行规划功能（支持调用旅行工具）
     *
     * @param message 用户消息
     * @param chatId 会话ID
     * @return AI回复内容
     */
    public String doChatWithTools(String message, String chatId) {
        log.info("旅行工具调用[{}]: {}", chatId, message);

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(travelTools)
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("旅行工具回复[{}]: {}", chatId, content);
        return content;
    }

    // AI 调用 MCP 服务

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * AI 旅行规划功能（调用 MCP 服务）
     *
     * @param message 用户消息
     * @param chatId 会话ID
     * @return AI回复内容
     */
    public String doChatWithMcp(String message, String chatId) {
        log.info("旅行MCP服务调用[{}]: {}", chatId, message);

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("旅行MCP服务回复[{}]: {}", chatId, content);
        return content;
    }

    /**
     * 快速旅行咨询（简化版，无需会话ID）
     *
     * @param message 用户消息
     * @return AI回复内容
     */
    public String quickTravelConsult(String message) {
        return doChat(message, "quick-" + System.currentTimeMillis());
    }

    /**
     * 获取系统信息
     *
     * @return 系统信息
     */
    public String getSystemInfo() {
        return "寰宇智导旅行规划系统 v1.0\n" +
                "功能：旅行规划、行程建议、预算管理、知识库问答\n" +
                "状态：运行正常\n" +
                "提示词：" + SYSTEM_PROMPT.substring(0, Math.min(100, SYSTEM_PROMPT.length())) + "...";
    }
}