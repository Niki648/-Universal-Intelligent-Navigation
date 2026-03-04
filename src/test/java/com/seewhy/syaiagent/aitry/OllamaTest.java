package com.seewhy.syaiagent.aitry;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class OllamaTest {

    @Autowired
    private ChatModel ollamaChatModel;

    @Test
    public void testOllama() {
        System.out.println("测试 Ollama...");
        String response = ollamaChatModel.call(new Prompt("你好，我是鱼皮"))
                .getResult()
                .getOutput()
                .getText();
        System.out.println("回复: " + response);
    }
}