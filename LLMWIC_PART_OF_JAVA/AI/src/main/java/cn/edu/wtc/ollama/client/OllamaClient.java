package cn.edu.wtc.ollama.client;

import cn.edu.wtc.ollama.config.OllamaConfig;
import okhttp3.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class OllamaClient {
    private final OllamaConfig config;
    private final OkHttpClient httpClient;

    public OllamaClient(OllamaConfig config) {
        this.config = config;
        this.httpClient = buildHttpClient();
    }

    private OkHttpClient buildHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(config.getConnectTimeout(), TimeUnit.SECONDS)
                .readTimeout(config.getReadTimeout(), TimeUnit.SECONDS)
                .writeTimeout(config.getWriteTimeout(), TimeUnit.SECONDS)
                .build();
    }

    public String chat(String model, List<Map<String, String>> messages) throws IOException {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("stream", false);

        String json = OllamaConfig.getObjectMapper().writeValueAsString(requestBody);
        RequestBody body = RequestBody.create(json, OllamaConfig.JSON);
        Request request = new Request.Builder()
                .url(config.getBaseUrlForModel(model) + "/api/chat") // 动态 URL
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "null";
                throw new IOException("请求失败: " + response.code() + " - " + response.message() + "\n" + errorBody);
            }
            String responseBody = response.body().string();
            Map<String, Object> responseMap = OllamaConfig.getObjectMapper().readValue(responseBody, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> messageMap = (Map<String, Object>) responseMap.get("message");
            return (String) messageMap.get("content");
        }
    }

    public boolean healthCheck() {
        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/api/tags") // 健康检查仍用默认 baseUrl
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            return false;
        }
    }

    public String listModels() throws IOException {
        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/api/tags") // 同样用默认 baseUrl
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("请求失败: " + response.code());
            return response.body().string();
        }
    }

    public String generate(String model, String prompt) throws IOException {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);
        String json = OllamaConfig.getObjectMapper().writeValueAsString(requestBody);
        RequestBody body = RequestBody.create(json, OllamaConfig.JSON);
        Request request = new Request.Builder()
                .url(config.getBaseUrlForModel(model) + "/api/generate") // 动态 URL
                .post(body)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "null";
                throw new IOException("请求失败: " + response.code() + " - " + response.message() + "\n" + errorBody);
            }
            String responseBody = response.body().string();
            Map<String, Object> responseMap = OllamaConfig.getObjectMapper().readValue(responseBody, Map.class);
            return (String) responseMap.get("response");
        }
    }
}