package com.seewhy.syaiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.seewhy.syaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果（要调用那些工具）
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        // 1、校验提示词，拼接用户提示词
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        // 2、调用 AI 大模型，获取工具调用结果
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
                ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();
            // 记录响应，用于等下 Act
            this.toolCallChatResponse = chatResponse;
            // 3、解析工具调用结果，获取要调用的工具
            // 助手消息
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 获取要调用的工具列表
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            // 输出提示信息
            String result = assistantMessage.getText();
            log.info(getName() + "的思考：" + result);
            log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s", toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);
            // 如果不需要调用工具，返回 false
            if (toolCallList.isEmpty()) {
                // 只有不调用工具时，才需要手动记录助手消息
                getMessageList().add(assistantMessage);
                return false;
            } else {
                // 需要调用工具时，无需记录助手消息，因为调用工具时会自动记录
                return true;
            }
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage());
            // DashScope 账户欠费/过期：不要重试，直接给用户明确提示并结束本次任务
            if (msg.contains("Arrearage") || msg.contains("overdue-payment") || msg.contains("\"code\":\"Arrearage\"")) {
                log.warn("{} 调用模型失败（疑似欠费/过期）：{}", getName(), msg);
                setState(AgentState.FINISHED);
                getMessageList().add(new AssistantMessage(
                        "[错误] 模型服务调用被拒绝：账户欠费/过期（DashScope 返回 Arrearage）。请为模型账户充值/续费，或更换可用的模型配置后再试。"
                ));
                return false;
            }
            log.error(getName() + "的思考过程遇到了问题：" + msg);
            getMessageList().add(new AssistantMessage("处理时遇到了错误：" + msg));
            return false;
        }
    }

    /**
     * 执行工具调用并处理结果
     *
     * @return 执行结果
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }
        // 调用工具
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        // 记录消息上下文，conversationHistory 已经包含了助手消息和工具调用返回的结果
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        // 判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (terminateToolCalled) {
            // 任务结束，更改状态
            setState(AgentState.FINISHED);
        }
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 返回的结果：" + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info(results);

        // 使用大模型对工具执行结果做一次面向用户的中文总结，
        // 避免把原始 JSON / 技术细节直接暴露给前端。
        try {
            ChatResponse summaryResponse = getChatClient()
                    .prompt()
                    .system(getSystemPrompt())
                    .user(
                            """
                            请用简短的中文，结合当前对话上下文，总结刚才工具执行的结果，直接面向终端用户说话。
                            要求：
                            1. 只说对用户有用的结论和下一步建议；
                            2. 不要展示任何原始 JSON、报错堆栈或过长的技术细节；
                            3. 如果工具执行成功（例如已经下载了图片），请明确告诉用户结果路径或状态；
                            4. 如果工具执行失败，请用一句通俗中文解释失败原因，并给出可以尝试的替代方案（如果有）。

                            工具执行结果如下（仅供你参考，不要原样照抄给用户）：
                            """ + results
                    )
                    .call()
                    .chatResponse();
            String summary = summaryResponse.getResult().getOutput().getText();
            // 将总结消息加入记忆
            getMessageList().add(summaryResponse.getResult().getOutput());
            return summary;
        } catch (Exception e) {
            log.warn(getName() + " 在生成工具结果总结时出错，将直接返回原始结果: {}", e.getMessage());
            return results;
        }
    }
}
