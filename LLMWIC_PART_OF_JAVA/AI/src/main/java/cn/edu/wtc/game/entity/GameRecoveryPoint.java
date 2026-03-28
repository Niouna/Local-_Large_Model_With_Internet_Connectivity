package cn.edu.wtc.game.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_recovery_point")
public class GameRecoveryPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 255)
    private String sessionId;

    @Column(name = "point_name", nullable = false, length = 100)
    private String pointName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "state_snapshot_id", nullable = false)
    private Long stateSnapshotId;

    @Column(name = "turn_number", nullable = false)
    private Integer turnNumber;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_auto", nullable = false)
    private Boolean isAuto = false;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getPointName() { return pointName; }
    public void setPointName(String pointName) { this.pointName = pointName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getStateSnapshotId() { return stateSnapshotId; }
    public void setStateSnapshotId(Long stateSnapshotId) { this.stateSnapshotId = stateSnapshotId; }

    public Integer getTurnNumber() { return turnNumber; }
    public void setTurnNumber(Integer turnNumber) { this.turnNumber = turnNumber; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Boolean getIsAuto() { return isAuto; }
    public void setIsAuto(Boolean isAuto) { this.isAuto = isAuto; }
}