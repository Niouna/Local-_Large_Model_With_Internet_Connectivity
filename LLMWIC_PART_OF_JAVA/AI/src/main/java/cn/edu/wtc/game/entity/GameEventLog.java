package cn.edu.wtc.game.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_event_log")
public class GameEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 255)
    private String sessionId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "turn_number", nullable = false)
    private Integer turnNumber;

    @Column(name = "user_input", columnDefinition = "TEXT")
    private String userInput;

    @Column(name = "narrative_mode", length = 20)
    private String narrativeMode;

    // 将 JSON 类型改为 TEXT
    @Column(name = "ai_response", columnDefinition = "TEXT")
    private String aiResponse;

    @Column(name = "state_delta", columnDefinition = "JSON")
    private String stateDelta;

    @Column(name = "process_time_ms")
    private Integer processTimeMs;

    @Column(name = "model_used", length = 100)
    private String modelUsed;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @PrePersist
    protected void onCreate() {
        if (eventTime == null) {
            eventTime = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Integer getTurnNumber() { return turnNumber; }
    public void setTurnNumber(Integer turnNumber) { this.turnNumber = turnNumber; }

    public String getUserInput() { return userInput; }
    public void setUserInput(String userInput) { this.userInput = userInput; }

    public String getNarrativeMode() { return narrativeMode; }
    public void setNarrativeMode(String narrativeMode) { this.narrativeMode = narrativeMode; }

    public String getAiResponse() { return aiResponse; }
    public void setAiResponse(String aiResponse) { this.aiResponse = aiResponse; }

    public String getStateDelta() { return stateDelta; }
    public void setStateDelta(String stateDelta) { this.stateDelta = stateDelta; }

    public Integer getProcessTimeMs() { return processTimeMs; }
    public void setProcessTimeMs(Integer processTimeMs) { this.processTimeMs = processTimeMs; }

    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
}