package cn.edu.wtc.ollama.client;

import cn.edu.wtc.ollama.config.OllamaConfig;
import cn.edu.wtc.ollama.callback.ChatStreamCallback;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final OllamaConfig config;
    private final OkHttpClient httpClient;

    public OllamaClient(OllamaConfig config) {
        this.config = config;
        this.httpClient = buildHttpClient();
    }

    private OkHttpClient buildHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(config.getConnectTimeout(), java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(config.getReadTimeout(), java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(config.getWriteTimeout(), java.util.concurrent.TimeUnit.SECONDS)
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
                .url(config.getBaseUrlForModel(model) + "/api/chat")
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
                .url(config.getBaseUrl() + "/api/tags")
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
                .url(config.getBaseUrl() + "/api/tags")
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
                .url(config.getBaseUrlForModel(model) + "/api/generate")
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

    /**
     * 流式聊天
     */
    public void chatStream(String model, List<Map<String, String>> messages, ChatStreamCallback callback) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("stream", true);

        try {
            String json = OllamaConfig.getObjectMapper().writeValueAsString(requestBody);
            RequestBody body = RequestBody.create(json, OllamaConfig.JSON);
            Request request = new Request.Builder()
                    .url(config.getBaseUrlForModel(model) + "/api/chat")
                    .post(body)
                    .build();

            httpClient.newCall(request).enqueue(new okhttp3.Callback() {
                private boolean inThink = false;
                private StringBuilder thinkBuffer = new StringBuilder();

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                    try (okhttp3.ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful()) {
                            callback.onError(new IOException("请求失败: " + response.code()));
                            return;
                        }

                        java.io.BufferedReader reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(responseBody.byteStream()));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.trim().isEmpty()) continue;

                            String data = line;
                            if (line.startsWith("data: ")) {
                                data = line.substring(6);
                            }

                            if ("[DONE]".equals(data)) {
                                callback.onComplete();
                                return;
                            }

                            try {
                                Map<String, Object> chunk = OllamaConfig.getObjectMapper()
                                        .readValue(data, Map.class);
                                Map<String, Object> message = (Map<String, Object>) chunk.get("message");
                                String content = (String) message.get("content");
                                if (content != null) {
                                    // 处理 think 标签
                                    String processed = processThinkTag(content);
                                    if (processed != null && !processed.isEmpty()) {
                                        callback.onChunk(processed);
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("解析流式块失败: {}", e.getMessage());
                            }
                        }
                        callback.onComplete();
                    }
                }

                private String processThinkTag(String chunk) {
                    StringBuilder result = new StringBuilder();

                    for (int i = 0; i < chunk.length(); i++) {
                        char c = chunk.charAt(i);

                        if (!inThink) {
                            // 检查是否进入 think 标签
                            if (chunk.startsWith("<think>", i)) {
                                inThink = true;
                                i += 6; // 跳过 "<think>"
                                continue;
                            }
                            result.append(c);
                        } else {
                            // 在 think 标签内，检查是否退出
                            if (chunk.startsWith("</think>", i)) {
                                inThink = false;
                                i += 7; // 跳过 "</think>"
                                continue;
                            }
                            // 忽略 think 内的内容
                        }
                    }

                    return result.toString();
                }

                @Override
                public void onFailure(okhttp3.Call call, IOException e) {
                    callback.onError(e);
                }
            });
        } catch (Exception e) {
            callback.onError(e);
        }
    }
}