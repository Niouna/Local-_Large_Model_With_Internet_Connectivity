package cn.edu.wtc.test;

import cn.edu.wtc.ollama.client.OllamaClient;
import cn.edu.wtc.ollama.config.OllamaConfig;
import cn.edu.wtc.ollama.model.Conversation;
import cn.edu.wtc.ollama.service.ChatService;
import cn.edu.wtc.ollama.service.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 对话记录测试类
 * 用于观察连续对话的实际效果，并保存完整日志
 */
public class DialogueLoggerTest {

    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) {
        System.out.println("=== 对话记录测试 ===");

        // 1. 初始化核心组件
        OllamaConfig config = new OllamaConfig();
        config.loadFromFile();
        // 临时开启详细日志，以便看到每次请求的细节（可选）
        config.setEnableLogging(true);

        OllamaClient client = new OllamaClient(config);
        SessionManager sessionManager = new SessionManager();
        ChatService chatService = new ChatService(client, sessionManager);

        // 2. 创建固定会话ID，便于观察历史
        String sessionId = "test-session-" + System.currentTimeMillis();
        String model = "qwen2.5:7b"; // 可根据需要修改

        // 3. 准备记录文件
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String logFile = "dialogue-log-" + timestamp + ".txt";

        List<DialogueEntry> dialogueHistory = new ArrayList<>();

        try (PrintWriter fileWriter = new PrintWriter(new FileWriter(logFile, true));
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("会话ID: " + sessionId);
            System.out.println("模型: " + model);
            System.out.println("日志将保存至: " + logFile);
            System.out.println("输入你的问题（输入 'quit' 退出）\n");

            while (true) {
                System.out.print("👤 你: ");
                String userInput = scanner.nextLine().trim();
                if (userInput.equalsIgnoreCase("quit") || userInput.equalsIgnoreCase("exit")) {
                    break;
                }
                if (userInput.isEmpty()) {
                    continue;
                }

                // 记录开始时间
                long startTime = System.currentTimeMillis();

                try {
                    // 调用连续对话 API
                    String aiResponse = chatService.chatWithSession(sessionId, model, userInput);

                    long endTime = System.currentTimeMillis();
                    long elapsed = endTime - startTime;

                    // 获取当前会话的快照
                    var conversation = chatService.getSession(sessionId);

                    // 创建对话条目
                    DialogueEntry entry = new DialogueEntry();
                    entry.setTimestamp(LocalDateTime.now().toString());
                    entry.setUserMessage(userInput);
                    entry.setAiResponse(aiResponse);
                    entry.setElapsedMs(elapsed);
                    entry.setTurnCount(conversation != null ? conversation.getTurnCount() : 0);
                    entry.setTotalTokens(conversation != null ? conversation.getTotalTokens() : 0);
                    entry.setFullHistory(conversation != null ? conversation.getFullHistory() : List.of());

                    dialogueHistory.add(entry);

                    // 打印到控制台
                    System.out.println("🤖 AI: " + aiResponse);
                    System.out.printf("[轮数:%d | token:%d | 耗时:%dms]\n",
                            entry.getTurnCount(), entry.getTotalTokens(), elapsed);

                    // 可选：打印完整历史（每轮对话后查看）
                    if (conversation != null) {
                        System.out.println("--- 当前历史摘要 ---");
                        conversation.printHistory(); // 假设 Conversation 有 printHistory 方法
                    }

                } catch (IOException e) {
                    System.err.println("请求失败: " + e.getMessage());
                }
                System.out.println();
            }

            // 4. 将完整对话记录写入文件（JSON格式便于阅读）
            ObjectNode root = mapper.createObjectNode();
            root.put("sessionId", sessionId);
            root.put("model", model);
            root.put("startTime", timestamp);
            root.put("endTime", LocalDateTime.now().toString());
            root.put("totalRounds", dialogueHistory.size());

            ArrayNode roundsArray = root.putArray("rounds");
            for (DialogueEntry entry : dialogueHistory) {
                ObjectNode roundNode = roundsArray.addObject();
                roundNode.put("timestamp", entry.getTimestamp());
                roundNode.put("user", entry.getUserMessage());
                roundNode.put("ai", entry.getAiResponse());
                roundNode.put("elapsedMs", entry.getElapsedMs());
                roundNode.put("turnCount", entry.getTurnCount());
                roundNode.put("totalTokens", entry.getTotalTokens());

                // 可选：记录完整历史（如果太多可省略）
                ArrayNode historyNode = roundNode.putArray("fullHistory");
                for (var msg : entry.getFullHistory()) {
                    ObjectNode msgNode = historyNode.addObject();
                    msgNode.put("role", msg.getRole());
                    msgNode.put("content", msg.getContent());
                    msgNode.put("timestamp", msg.getTimestamp());
                }
            }

            fileWriter.println(mapper.writeValueAsString(root));
            System.out.println("✅ 对话记录已保存至: " + logFile);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 简单的对话条目POJO
    static class DialogueEntry {
        private String timestamp;
        private String userMessage;
        private String aiResponse;
        private long elapsedMs;
        private int turnCount;
        private int totalTokens;
        private List<Conversation.Message> fullHistory;

        // getters and setters
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        public String getUserMessage() { return userMessage; }
        public void setUserMessage(String userMessage) { this.userMessage = userMessage; }
        public String getAiResponse() { return aiResponse; }
        public void setAiResponse(String aiResponse) { this.aiResponse = aiResponse; }
        public long getElapsedMs() { return elapsedMs; }
        public void setElapsedMs(long elapsedMs) { this.elapsedMs = elapsedMs; }
        public int getTurnCount() { return turnCount; }
        public void setTurnCount(int turnCount) { this.turnCount = turnCount; }
        public int getTotalTokens() { return totalTokens; }
        public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
        public List<Conversation.Message> getFullHistory() { return fullHistory; }
        public void setFullHistory(List<Conversation.Message> fullHistory) { this.fullHistory = fullHistory; }
    }
}