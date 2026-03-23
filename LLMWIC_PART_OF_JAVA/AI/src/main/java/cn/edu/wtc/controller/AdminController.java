package cn.edu.wtc.controller;

import cn.edu.wtc.manager.OllamaServiceManager;
import cn.edu.wtc.memory.entity.ConversationMemory;
import cn.edu.wtc.memory.entity.RagRequestLog;
import cn.edu.wtc.memory.repository.ConversationMemoryRepository;
import cn.edu.wtc.memory.repository.RagRequestLogRepository;
import cn.edu.wtc.memory.service.MemoryDebugSnapshotService;
import cn.edu.wtc.memory.service.MemoryService;
import cn.edu.wtc.ollama.model.Conversation;
import cn.edu.wtc.ollama.service.SessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private SessionManager sessionManager;
    @Autowired
    private ConversationMemoryRepository memoryRepository;
    @Autowired
    private RagRequestLogRepository ragLogRepository;  // 新增注入
    @Autowired
    private MemoryService memoryService;
    @Autowired
    private MemoryDebugSnapshotService snapshotService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ------------------ 状态仪表盘 ------------------
    @GetMapping("/status/ollama")
    public Map<String, Object> ollamaStatus() {
        Map<String, Object> result = new HashMap<>();
        boolean gpuRunning = OllamaServiceManager.isServiceRunning(11434);
        result.put("gpu", Map.of(
                "port", 11434,
                "running", gpuRunning,
                "models", gpuRunning ? OllamaServiceManager.getModels(11434) : "服务未运行"
        ));
        boolean cpuRunning = OllamaServiceManager.isServiceRunning(11435);
        result.put("cpu", Map.of(
                "port", 11435,
                "running", cpuRunning,
                "models", cpuRunning ? OllamaServiceManager.getModels(11435) : "服务未运行"
        ));
        return result;
    }

    @GetMapping("/status/sessions")
    public Map<String, Object> sessionStats() {
        int activeCount = sessionManager.getAllSessions().size();
        int totalTurns = sessionManager.getAllSessions().stream()
                .mapToInt(Conversation::getTurnCount).sum();
        return Map.of("activeCount", activeCount, "totalTurns", totalTurns);
    }

    @GetMapping("/status/memories")
    public Map<String, Object> memoryStats() {
        long total = memoryRepository.count();
        return Map.of("total", total);
    }

    @GetMapping("/status/snapshot-last")
    public Map<String, Object> lastSnapshot() {
        return Map.of("lastSnapshotTime", snapshotService.getLastSnapshotTime());
    }

    // ------------------ 会话管理 ------------------
    @GetMapping("/sessions")
    public List<Map<String, Object>> listSessions() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Conversation conv : sessionManager.getAllSessions()) {
            Map<String, Object> item = new HashMap<>();
            item.put("sessionId", conv.getSessionId());
            item.put("model", conv.getModel());
            item.put("turnCount", conv.getTurnCount());
            item.put("totalTokens", conv.getTotalTokens());
            item.put("lastActivityTime", conv.getLastActivityTime());
            list.add(item);
        }
        return list;
    }

    @GetMapping("/sessions/{sessionId}")
    public Map<String, Object> getSessionDetail(@PathVariable String sessionId) {
        Conversation conv = sessionManager.getSession(sessionId);
        if (conv == null) {
            return Map.of("error", "会话不存在");
        }
        Map<String, Object> detail = new HashMap<>();
        detail.put("sessionId", conv.getSessionId());
        detail.put("model", conv.getModel());
        detail.put("history", conv.getFullHistory().stream().map(msg ->
                Map.of("role", msg.getRole(), "content", msg.getContent(), "timestamp", msg.getTimestamp())
        ).toList());

        String memoryContext = memoryService.assembleMemoryContext(sessionId);
        detail.put("memoryContext", memoryContext);

        return detail;
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Map<String, String> endSession(@PathVariable String sessionId) {
        sessionManager.endSession(sessionId);
        return Map.of("result", "success");
    }

    // ------------------ 记忆管理 ------------------
    @GetMapping("/memories")
    public Page<ConversationMemory> listMemories(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            Pageable pageable) {

        Specification<ConversationMemory> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (sessionId != null && !sessionId.isEmpty()) {
                predicates.add(cb.like(root.get("sessionId"), "%" + sessionId + "%"));
            }
            if (level != null) {
                predicates.add(cb.equal(root.get("level"), level));
            }
            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return memoryRepository.findAll(spec, pageable);
    }

    @DeleteMapping("/memories/{id}")
    public Map<String, String> deleteMemory(@PathVariable Long id) {
        memoryRepository.deactivateByIds(List.of(id));
        return Map.of("result", "success");
    }

    @DeleteMapping("/memories/all")
    public Map<String, String> deleteAllMemories(@RequestParam(defaultValue = "false") boolean confirm) {
        if (!confirm) {
            return Map.of("error", "请确认操作");
        }
        memoryRepository.deleteAll();
        jdbcTemplate.execute("ALTER TABLE conversation_memories AUTO_INCREMENT = 1");
        return Map.of("result", "success");
    }

    // ------------------ 新增：RAG 日志追踪 ------------------

    @GetMapping("/rag-logs")
    public Page<RagRequestLog> listRagLogs(
            @RequestParam(required = false) String sessionId,
            Pageable pageable) {

        Specification<RagRequestLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (sessionId != null && !sessionId.isEmpty()) {
                predicates.add(cb.like(root.get("sessionId"), "%" + sessionId + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return ragLogRepository.findAll(spec, pageable);
    }

    @GetMapping("/rag-logs/{id}")
    public RagRequestLog getRagLogDetail(@PathVariable Long id) {
        return ragLogRepository.findById(id).orElse(null);
    }
}