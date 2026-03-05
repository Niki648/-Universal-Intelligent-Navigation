package com.seewhy.syaiagent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具：基于 SearchAPI 调用百度搜索，供 SyManus 智能体进行通用网页检索。
 * 图片类搜索建议使用 {@link ImageSearchTool}。
 */
public class WebSearchTool {

    // SearchAPI 的搜索接口地址
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for information from Baidu Search Engine. Use for general web search; for image search prefer searchImage tool.")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        try {
            String response = HttpUtil.createGet(SEARCH_API_URL)
                    .form(paramMap)
                    .setConnectionTimeout(8000)
                    .setReadTimeout(15000)
                    .execute()
                    .body();
            JSONObject jsonObject = JSONUtil.parseObj(response);
            // API 可能返回 error 字段（配额、key 无效等）
            if (jsonObject.containsKey("error")) {
                Object err = jsonObject.get("error");
                return "Baidu search API error: " + (err != null ? err.toString() : "unknown");
            }
            JSONArray organicResults = jsonObject.getJSONArray("organic_results");
            if (organicResults == null || organicResults.isEmpty()) {
                return "Baidu search returned no results. Try different keyword or use searchImage for pictures.";
            }
            // 安全取前 5 条，避免 toIndex 越界
            int take = Math.min(5, organicResults.size());
            List<Object> objects = organicResults.subList(0, take);
            String result = objects.stream().map(obj -> {
                JSONObject tmpJSONObject = (JSONObject) obj;
                return tmpJSONObject.toString();
            }).collect(Collectors.joining(","));
            return result;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return "Error searching Baidu: " + msg + ". Suggest: try searchImage for image search.";
        }
    }
}
