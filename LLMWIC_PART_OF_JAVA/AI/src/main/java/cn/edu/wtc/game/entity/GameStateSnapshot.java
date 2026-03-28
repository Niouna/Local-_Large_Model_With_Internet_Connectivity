package cn.edu.wtc.game.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_state_snapshot")
public class GameStateSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 255)
    private String sessionId;

    @Column(name = "world_state", columnDefinition = "JSON")
    private String worldState;

    @Column(name = "character_state", columnDefinition = "JSON")
    private String characterState;

    @Column(name = "plot_state", columnDefinition = "JSON")
    private String plotState;

    @Column(name = "narrative_memory", columnDefinition = "TEXT")
    private String narrativeMemory;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "turn_number", nullable = false)
    private Integer turnNumber = 0;

    @Column(name = "snapshot_time", nullable = false)
    private LocalDateTime snapshotTime;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = false;

    @PrePersist
    protected void onCreate() {
        if (snapshotTime == null) {
            snapshotTime = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getWorldState() { return worldState; }
    public void setWorldState(String worldState) { this.worldState = worldState; }

    public String getCharacterState() { return characterState; }
    public void setCharacterState(String characterState) { this.characterState = characterState; }

    public String getPlotState() { return plotState; }
    public void setPlotState(String plotState) { this.plotState = plotState; }

    public String getNarrativeMemory() { return narrativeMemory; }
    public void setNarrativeMemory(String narrativeMemory) { this.narrativeMemory = narrativeMemory; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Integer getTurnNumber() { return turnNumber; }
    public void setTurnNumber(Integer turnNumber) { this.turnNumber = turnNumber; }

    public LocalDateTime getSnapshotTime() { return snapshotTime; }
    public void setSnapshotTime(LocalDateTime snapshotTime) { this.snapshotTime = snapshotTime; }

    public Boolean getIsCurrent() { return isCurrent; }
    public void setIsCurrent(Boolean isCurrent) { this.isCurrent = isCurrent; }
}