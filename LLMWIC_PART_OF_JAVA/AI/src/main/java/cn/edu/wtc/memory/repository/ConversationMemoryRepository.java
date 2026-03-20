package cn.edu.wtc.memory.repository;

import cn.edu.wtc.memory.entity.ConversationMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ConversationMemoryRepository extends JpaRepository<ConversationMemory, Long>,
        JpaSpecificationExecutor<ConversationMemory> { // 扩展以支持动态查询

    List<ConversationMemory> findBySessionIdAndLevelAndIsActiveTrueOrderByCreatedAtDesc(String sessionId, Integer level);

    List<ConversationMemory> findBySessionIdAndLevelAndIsActiveTrueOrderByCreatedAtAsc(String sessionId, Integer level);

    @Modifying
    @Transactional
    @Query("UPDATE ConversationMemory m SET m.isActive = false WHERE m.id IN :ids")
    void deactivateByIds(@Param("ids") List<Long> ids);

    long countBySessionIdAndLevelAndIsActiveTrue(String sessionId, Integer level);

    List<ConversationMemory> findTop5BySessionIdAndLevelAndIsActiveTrueOrderByCreatedAtAsc(String sessionId, Integer level);

    ConversationMemory findFirstBySessionIdAndLevelAndIsActiveTrueOrderByCreatedAtDesc(String sessionId, Integer level);

    ConversationMemory findTopBySessionIdAndLevelAndIsActiveTrueOrderByCreatedAtDesc(String sessionId, Integer level);

    ConversationMemory findBySessionIdAndLevelAndIsActiveTrue(String sessionId, Integer level);
}