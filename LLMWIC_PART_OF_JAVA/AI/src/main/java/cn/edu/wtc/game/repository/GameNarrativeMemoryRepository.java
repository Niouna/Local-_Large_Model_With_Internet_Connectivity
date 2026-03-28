package cn.edu.wtc.game.repository;

import cn.edu.wtc.game.memory.GameNarrativeMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GameNarrativeMemoryRepository extends JpaRepository<GameNarrativeMemory, Long>, JpaSpecificationExecutor<GameNarrativeMemory> {

    List<GameNarrativeMemory> findBySessionIdOrderByTurnNumberDesc(String sessionId);

    List<GameNarrativeMemory> findBySessionIdAndMemoryTypeOrderByTurnNumberDesc(String sessionId, String memoryType);

    @Query("SELECT m FROM GameNarrativeMemory m WHERE m.sessionId = :sessionId AND m.importance >= :minImportance ORDER BY m.turnNumber DESC")
    List<GameNarrativeMemory> findImportantMemories(@Param("sessionId") String sessionId, @Param("minImportance") Integer minImportance);

    @Query(value = "SELECT * FROM game_narrative_memory WHERE session_id = :sessionId ORDER BY turn_number DESC LIMIT :limit", nativeQuery = true)
    List<GameNarrativeMemory> findRecentMemories(@Param("sessionId") String sessionId, @Param("limit") int limit);

    void deleteBySessionId(String sessionId);
}