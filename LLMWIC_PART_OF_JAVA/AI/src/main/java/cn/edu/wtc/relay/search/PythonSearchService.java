package cn.edu.wtc.relay.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
public class PythonSearchService implements SearchService {
    private static final String PYTHON_API_URL = "http://127.0.0.1:5000/search";
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(240, TimeUnit.SECONDS)   // 关键：增加读超时
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String search(String query, int maxResults) {
        System.out.println("调用 Python 搜索服务，查询词: " + query);
        long start = System.currentTimeMillis();
        String jsonBody = String.format("{\"query\":\"%s\",\"max_results\":%d}", query, maxResults);
        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(PYTHON_API_URL)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            System.out.println("Python 搜索耗时: " + (System.currentTimeMillis() - start) + "ms");
            if (!response.isSuccessful()) {
                System.err.println("搜索服务返回错误码: " + response.code());
                return "搜索服务异常...";
            }
            String responseBody = response.body().string();
            System.out.println("Python 返回内容长度: " + responseBody.length());
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("summary_for_llm")) {
                return root.get("summary_for_llm").asText();
            } else {
                System.err.println("响应中没有 summary_for_llm 字段");
                return fallbackFormat(root);
            }
        } catch (IOException e) {
            System.err.println("搜索请求异常: " + e.getMessage());
            e.printStackTrace();
            return "搜索请求异常: " + e.getMessage();
        }
    }

    private String fallbackFormat(JsonNode root) {
        // 与之前相同...
        StringBuilder sb = new StringBuilder("以下是联网搜索到的信息：\n\n");
        JsonNode results = root.get("results");
        if (results != null && results.isArray()) {
            for (JsonNode item : results) {
                sb.append("【").append(item.get("title").asText()).append("】\n");
                sb.append(item.get("snippet").asText()).append("\n");
                sb.append("链接：").append(item.get("link").asText()).append("\n\n");
            }
        } else {
            sb.append("未找到相关结果。");
        }
        return sb.toString();
    }
}