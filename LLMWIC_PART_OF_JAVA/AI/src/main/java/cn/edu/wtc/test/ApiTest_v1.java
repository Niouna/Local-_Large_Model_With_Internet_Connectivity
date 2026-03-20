package cn.edu.wtc.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;

/**
 * 简单的 API 测试客户端
 * 向本地 OpenAI 兼容服务发送请求，并打印回复
 * 需要自己启动 LocalAIApiApplication.java
 */
public class ApiTest_v1 {

    private static final String API_URL = "http://localhost:8080/v1/chat/completions";
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        System.out.println("=== 本地 AI API 测试客户端 ===");
        System.out.println("输入你的问题（输入 'quit' 退出）\n");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("👤 你: ");
                String userInput = scanner.nextLine().trim();
                if (userInput.equalsIgnoreCase("quit") || userInput.equalsIgnoreCase("exit")) {
                    break;
                }
                if (userInput.isEmpty()) {
                    continue;
                }

                try {
                    String aiResponse = sendRequest(userInput);
                    System.out.println("🤖 AI: " + aiResponse);
                } catch (Exception e) {
                    System.err.println("请求失败: " + e.getMessage());
                }
                System.out.println();
            }
        }
        System.out.println("测试结束。");
    }

    private static String sendRequest(String message) throws Exception {
        // 构建请求体（符合 OpenAI 格式）
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", "qwen2.5:7b");  // 你可以改成其他已安装的模型
        requestBody.put("temperature", 0.7);
        requestBody.put("stream", false);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", message);

        String json = mapper.writeValueAsString(requestBody);

        // 创建 HTTP 客户端
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        // 发送请求
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP 错误: " + response.statusCode() + "\n" + response.body());
        }

        // 解析响应，提取 AI 回答
        JsonNode root = mapper.readTree(response.body());
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode messageNode = choices.get(0).get("message");
            if (messageNode != null) {
                return messageNode.get("content").asText();
            }
        }
        throw new RuntimeException("无法解析响应: " + response.body());
    }
}