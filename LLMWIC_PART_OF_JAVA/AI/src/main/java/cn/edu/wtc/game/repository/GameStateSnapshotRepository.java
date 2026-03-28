package cn.edu.wtc.game.repository;

import cn.edu.wtc.game.entity.GameStateSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface GameStateSnapshotRepository extends JpaRepository<GameStateSnapshot, Long>, JpaSpecificationExecutor<GameStateSnapshot> {

    // 方法1：返回 Optional（单个结果，期望唯一）
    Optional<GameStateSnapshot> findBySessionIdAndIsCurrentTrue(String sessionId);

    // 方法2：返回 List（多个结果）- 改名为 findAllBySessionIdAndIsCurrentTrue
    List<GameStateSnapshot> findAllBySessionIdAndIsCurrentTrue(String sessionId);

    @Modifying
    @Transactional
    @Query("UPDATE GameStateSnapshot s SET s.isCurrent = false WHERE s.sessionId = :sessionId AND s.isCurrent = true")
    void updateCurrentFlag(@Param("sessionId") String sessionId);

    Optional<GameStateSnapshot> findFirstBySessionIdAndTurnNumberLessThanEqualOrderByTurnNumberDesc(
            String sessionId, Integer turnNumber);

    long countBySessionId(String sessionId);

    Optional<GameStateSnapshot> findFirstBySessionIdOrderBySnapshotTimeDesc(String sessionId);
}