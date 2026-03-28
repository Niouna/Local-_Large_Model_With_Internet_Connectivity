package cn.edu.wtc.game.repository;

import cn.edu.wtc.game.entity.GameEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface GameEventLogRepository extends JpaRepository<GameEventLog, Long>, JpaSpecificationExecutor<GameEventLog> {

    Optional<GameEventLog> findBySessionIdAndTurnNumber(String sessionId, Integer turnNumber);

    List<GameEventLog> findBySessionIdOrderByTurnNumberAsc(String sessionId);

    List<GameEventLog> findBySessionIdAndTurnNumberGreaterThan(String sessionId, Integer turnNumber);

    long countBySessionId(String sessionId);
}