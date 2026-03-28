package cn.edu.wtc.game.repository;

import cn.edu.wtc.game.entity.GameRecoveryPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface GameRecoveryPointRepository extends JpaRepository<GameRecoveryPoint, Long>, JpaSpecificationExecutor<GameRecoveryPoint> {

    List<GameRecoveryPoint> findBySessionIdOrderByCreatedAtDesc(String sessionId);

    List<GameRecoveryPoint> findBySessionIdAndIsAutoOrderByCreatedAtDesc(String sessionId, Boolean isAuto);

    void deleteBySessionId(String sessionId);
}