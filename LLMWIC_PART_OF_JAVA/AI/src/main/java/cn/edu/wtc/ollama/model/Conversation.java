package cn.edu.wtc.ollama.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话管理类 - 维护对话历史
 */
public class Conversation {
    private final String sessionId;
    private String id;
    private final List<Message> history;
    private final int maxHistoryLength;
    private int totalTokens = 0;
    private final String model;
    private LocalDateTime lastActivityTime;  // 新增最后活动时间

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Conversation(String sessionId, String model) {
        this(sessionId, model, 20); // 默认保存最近20轮对话
    }

    public Conversation(String sessionId, String model, int maxHistoryLength) {
        this.sessionId = sessionId;
        this.model = model;
        this.maxHistoryLength = maxHistoryLength;
        this.history = new ArrayList<>();
        this.lastActivityTime = LocalDateTime.now(); // 初始化
    }

    /**
     * 消息类
     */
    public static class Message {
        private final String role;  // "user", "assistant", "system"
        private final String content;
        private final long timestamp;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }

        public String getRole() { return role; }
        public String getContent() { return content; }
        public long getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s",
                    new java.util.Date(timestamp),
                    role,
                    content.length() > 50 ? content.substring(0, 50) + "..." : content
            );
        }

        /**
         * 转换为API请求格式
         */
        public java.util.Map<String, String> toApiFormat() {
            java.util.Map<String, String> map = new java.util.HashMap<>();
            map.put("role", role);
            map.put("content", content);
            return map;
        }
    }

    /**
     * 添加用户消息
     */
    public void addUserMessage(String content) {
        addMessage(new Message("user", content));
    }

    /**
     * 添加AI回复
     */
    public void addAssistantMessage(String content) {
        addMessage(new Message("assistant", content));
    }

    /**
     * 添加系统消息
     */
    public void addSystemMessage(String content) {
        addMessage(new Message("system", content));
    }

    private void addMessage(Message message) {
        history.add(message);
        totalTokens += estimateTokens(message.getContent());
        // 更新最后活动时间
        this.lastActivityTime = LocalDateTime.now();

        // 如果历史记录超过最大长度，移除最旧的消息
        if (history.size() > maxHistoryLength) {
            Message removed = history.remove(0);
            totalTokens -= estimateTokens(removed.getContent());
        }
    }

    /**
     * 估算token数量（简单估算）
     */
    private int estimateTokens(String text) {
        // 粗略估算：中文大约1个汉字=1.3个token，英文1个单词=1.3个token
        // 这里简化处理：按字符数/2估算
        return text.length() / 2;
    }

    /**
     * 获取对话历史（API格式）
     */
    public List<java.util.Map<String, String>> getHistoryForApi() {
        List<java.util.Map<String, String>> apiHistory = new ArrayList<>();
        for (Message message : history) {
            apiHistory.add(message.toApiFormat());
        }
        return apiHistory;
    }

    /**
     * 获取完整对话历史
     */
    public List<Message> getFullHistory() {
        return new ArrayList<>(history);
    }

    /**
     * 获取最近N轮对话
     */
    public List<Message> getRecentHistory(int n) {
        if (n >= history.size()) {
            return new ArrayList<>(history);
        }
        return new ArrayList<>(history.subList(history.size() - n, history.size()));
    }

    /**
     * 清空对话历史
     */
    public void clearHistory() {
        history.clear();
        totalTokens = 0;
        this.lastActivityTime = LocalDateTime.now(); // 清空也视为活动
    }

    /**
     * 获取会话ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取模型名称
     */
    public String getModel() {
        return model;
    }

    /**
     * 获取总token数估算
     */
    public int getTotalTokens() {
        return totalTokens;
    }

    /**
     * 获取对话轮数
     */
    public int getTurnCount() {
        return history.size();
    }

    /**
     * 获取最后活动时间（新增）
     */
    public LocalDateTime getLastActivityTime() {
        return lastActivityTime;
    }

    /**
     * 获取会话摘要
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 会话摘要 ===\n");
        sb.append("会话ID: ").append(sessionId).append("\n");
        sb.append("使用模型: ").append(model).append("\n");
        sb.append("对话轮数: ").append(history.size()).append("\n");
        sb.append("估算token: ").append(totalTokens).append("\n");
        sb.append("历史记录:\n");

        for (int i = 0; i < Math.min(history.size(), 5); i++) {
            sb.append("  ").append(history.get(i)).append("\n");
        }

        if (history.size() > 5) {
            sb.append("  ... 还有").append(history.size() - 5).append("条记录\n");
        }

        return sb.toString();
    }

    /**
     * 打印对话历史
     */
    public void printHistory() {
        System.out.println("=== 对话历史 ===");
        for (int i = 0; i < history.size(); i++) {
            Message msg = history.get(i);
            String roleChar = msg.getRole().equals("user") ? "👤" : "🤖";
            System.out.printf("%d. %s %s: %s\n",
                    i + 1,
                    roleChar,
                    msg.getRole(),
                    msg.getContent()
            );
        }
        System.out.println("================");
    }
}