package cn.edu.wtc.dto;

import jakarta.validation.constraints.NotEmpty;
import com.fasterxml.jackson.annotation.JsonProperty; // 导入注解
import java.util.List;

public class ChatCompletionRequest {
    private String model = "qwen2.5:7b";
    @NotEmpty
    private List<Message> messages;

    // 原有的 sessionId 字段
    private String sessionId;

    // 新增：用于接收前端的联网搜索开关状态
    @JsonProperty("isWebSearchEnabled") // 指定JSON中的字段名
    private Boolean isWebSearchEnabled;

    private Double temperature = 0.7;
    private Integer maxTokens;
    private Boolean stream = false;

    // getters and setters

    // 原有字段的getter/setter
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public Boolean getStream() { return stream; }
    public void setStream(Boolean stream) { this.stream = stream; }

    // 新增字段的getter/setter
    public Boolean isWebSearchEnabled() { return isWebSearchEnabled; }
    public void setWebSearchEnabled(Boolean webSearchEnabled) { this.isWebSearchEnabled = webSearchEnabled; }

    // 原有字段的getter/setter
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public static class Message {
        private String role;   // "user", "assistant", "system"
        private String content;

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}