package cn.edu.wtc.memory.repository;

import cn.edu.wtc.memory.entity.RagRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RagRequestLogRepository extends
        JpaRepository<RagRequestLog, Long>,
        JpaSpecificationExecutor<RagRequestLog> {
}