package cn.edu.wtc.game.repository;

import cn.edu.wtc.game.entity.GameWorldProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameWorldProfileRepository extends JpaRepository<GameWorldProfile, Long> {
    Optional<GameWorldProfile> findBySessionId(String sessionId);
}