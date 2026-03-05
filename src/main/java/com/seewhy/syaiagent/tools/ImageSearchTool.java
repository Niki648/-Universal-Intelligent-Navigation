package com.seewhy.syaiagent.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 图片搜索工具（Pexels API），供 SyManus 在用户要求「搜索/下载 XX 图片」时使用。
 */
@Component
public class ImageSearchTool {

    private static final String PEXELS_API_URL = "https://api.pexels.com/v1/search";

    @Value("${pexels.api-key:}")
    private String apiKey;

    @Tool(description = "Search for image URLs by keyword (e.g. ocean, panda, 大海, 东方明珠). Returns comma-separated image URLs. Use this when user asks to search or download a picture; then use downloadResource with one of the returned URLs.")
    public String searchImage(@ToolParam(description = "Search query keyword") String query) {
        if (apiKey == null || apiKey.isBlank()) {
            return "Pexels API key not configured. Set pexels.api-key or PEXELS_API_KEY. You can try searchWeb + downloadResource instead.";
        }
        try {
            List<String> urls = searchMediumImages(query);
            if (urls.isEmpty()) {
                return "No images found for: " + query + ". Try another keyword.";
            }
            return String.join(",", urls);
        } catch (Exception e) {
            return "Error search image: " + e.getMessage();
        }
    }

    private List<String> searchMediumImages(String query) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", apiKey);
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("per_page", 5);

        String response = HttpUtil.createGet(PEXELS_API_URL)
                .addHeaders(headers)
                .form(params)
                .setConnectionTimeout(8000)
                .setReadTimeout(15000)
                .execute()
                .body();

        var photos = JSONUtil.parseObj(response).getJSONArray("photos");
        if (photos == null || photos.isEmpty()) {
            return new ArrayList<>();
        }
        return photos.stream()
                .map(obj -> (JSONObject) obj)
                .map(photo -> photo.getJSONObject("src"))
                .filter(src -> src != null)
                .map(src -> src.getStr("medium"))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }
}
