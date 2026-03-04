package com.seewhy.syaiagent;

import okhttp3.*;

import java.io.IOException;

public class CorrectApiTest {

    public static void main(String[] args) {
        System.out.println("🔧 正确测试 Dashscope API...\n");

        // 正确的 API 端点
        testCorrectEndpoint();
    }

    private static void testCorrectEndpoint() {
        OkHttpClient client = new OkHttpClient();

        // 正确的请求体
        String json = """
            {
                "model": "qwen-plus",
                "input": {
                    "messages": [
                        {
                            "role": "user",
                            "content": "你好"
                        }
                    ]
                },
                "parameters": {}
            }
            """;

        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));

        // 正确的 API 端点
        String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer sk-3fe5b9bfa6004c24ad600c48be06a96f")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            System.out.println("状态码: " + response.code());
            System.out.println("响应头: " + response.headers());

            if (response.body() != null) {
                String responseBody = response.body().string();
                System.out.println("响应体: " + responseBody);

                if (response.code() == 200) {
                    System.out.println("\n✅ API 连接成功！");
                } else {
                    System.out.println("\n❌ API 返回错误: " + response.code());
                }
            }
        } catch (IOException e) {
            System.err.println("❌ 网络错误: " + e.getMessage());
        }
    }
}