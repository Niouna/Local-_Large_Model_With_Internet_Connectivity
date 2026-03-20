package cn.edu.wtc.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
//废弃，暂时无解决办法
public class ApiTest_v2 {

    private static final String API_URL = "http://localhost:8080/v1/chat/completions";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static Process serverProcess;
    private static Thread shutdownHook;

    public static void main(String[] args) {
        Path projectRoot = Paths.get("D:", "个人项目", "Local _Large_Model_With_Internet_Connectivity", "LLMWIC_PART_OF_JAVA");
        if (!projectRoot.toFile().exists()) {
            System.err.println("❌ 项目根目录不存在: " + projectRoot);
            System.exit(1);
        }

        boolean serverStarted = startLocalServer(projectRoot);
        if (!serverStarted) {
            System.err.println("⚠️ 服务启动失败，请检查日志");
            System.exit(1);
        }

        shutdownHook = new Thread(() -> {
            System.out.println("\n正在关闭本地服务...");
            stopServer();
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        runInteractiveTest();

        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {}
        stopServer();
        System.out.println("测试结束。");
    }

    private static boolean startLocalServer(Path projectRoot) {
        System.out.println("🚀 正在启动本地服务...");

        String[] cmd = {"cmd.exe", "/c", "mvn", "spring-boot:run", "-pl", "AI", "-f", projectRoot.resolve("pom.xml").toString()};

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(projectRoot.toFile());
        // 将服务输出重定向到文件，避免干扰控制台
        File logFile = projectRoot.resolve("target").resolve("server-output.log").toFile();
        logFile.getParentFile().mkdirs();
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        pb.redirectErrorStream(true);

        try {
            serverProcess = pb.start();
            // 等待服务启动：最多尝试 30 秒，每隔 2 秒检查一次端口是否可连接
            if (waitForServer(30)) {
                System.out.println("✅ 服务启动成功！日志位置: " + logFile);
                return true;
            } else {
                System.err.println("❌ 服务启动超时，请查看日志: " + logFile);
                serverProcess.destroyForcibly();
                return false;
            }
        } catch (IOException e) {
            System.err.println("❌ 启动服务失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 等待服务就绪：直接尝试发送一个简单的请求（比如获取模型列表），成功即认为服务已启动
     */
    private static boolean waitForServer(int timeoutSeconds) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        long start = System.currentTimeMillis();
        System.out.print("等待服务就绪");

        while (System.currentTimeMillis() - start < timeoutSeconds * 1000L) {
            try {
                // 尝试调用 /v1/models 端点（Ollama 兼容 API 通常有这个）
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/v1/models"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                // 只要返回 2xx 或 4xx（服务存在但请求可能有问题），都认为服务已启动
                if (response.statusCode() >= 200 && response.statusCode() < 500) {
                    System.out.println(" ✅");
                    return true;
                }
            } catch (Exception e) {
                // 忽略，继续等待
            }

            try {
                Thread.sleep(2000);
                System.out.print(".");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println(" ❌ 超时");
        return false;
    }

    private static void stopServer() {
        if (serverProcess != null && serverProcess.isAlive()) {
            System.out.println("正在停止服务...");
            serverProcess.destroy();
            try {
                if (!serverProcess.waitFor(10, TimeUnit.SECONDS)) {
                    serverProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                serverProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            System.out.println("服务已停止");
        }
    }

    private static void runInteractiveTest() {
        System.out.println("\n=== 本地 AI API 测试客户端 ===");
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
                    e.printStackTrace();
                }
                System.out.println();
            }
        }
    }

    private static String sendRequest(String message) throws Exception {
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", "qwen2.5:7b");
        requestBody.put("temperature", 0.7);
        requestBody.put("stream", false);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", message);

        String json = mapper.writeValueAsString(requestBody);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP 错误 " + response.statusCode() + ": " + response.body());
        }

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