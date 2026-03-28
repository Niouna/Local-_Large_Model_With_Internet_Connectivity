package cn.edu.wtc.controller;

import cn.edu.wtc.game.entity.CharacterTraitDefinition;
import cn.edu.wtc.game.entity.GameEventLog;
import cn.edu.wtc.game.entity.GameRecoveryPoint;
import cn.edu.wtc.game.entity.GameStateSnapshot;
import cn.edu.wtc.game.memory.GameNarrativeMemory;
import cn.edu.wtc.game.repository.CharacterTraitDefinitionRepository;
import cn.edu.wtc.game.repository.GameEventLogRepository;
import cn.edu.wtc.game.repository.GameNarrativeMemoryRepository;
import cn.edu.wtc.game.repository.GameRecoveryPointRepository;
import cn.edu.wtc.game.repository.GameStateSnapshotRepository;
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
    private RagRequestLogRepository ragLogRepository;
    @Autowired
    private MemoryService memoryService;
    @Autowired
    private MemoryDebugSnapshotService snapshotService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 游戏相关 Repository
    @Autowired
    private GameEventLogRepository gameEventLogRepository;
    @Autowired
    private GameNarrativeMemoryRepository gameNarrativeMemoryRepository;
    @Autowired
    private GameRecoveryPointRepository gameRecoveryPointRepository;
    @Autowired
    private GameStateSnapshotRepository gameStateSnapshotRepository;

    // 特质定义 Repository
    @Autowired
    private CharacterTraitDefinitionRepository traitRepository;

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

    // ------------------ RAG 日志追踪 ------------------
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

    // ------------------ 游戏事件 ------------------
    @GetMapping("/game-events")
    public Page<GameEventLog> getGameEvents(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String eventType,
            Pageable pageable) {
        Specification<GameEventLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (sessionId != null && !sessionId.isEmpty()) {
                predicates.add(cb.like(root.get("sessionId"), "%" + sessionId + "%"));
            }
            if (eventType != null && !eventType.isEmpty()) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return gameEventLogRepository.findAll(spec, pageable);
    }

    @GetMapping("/game-events/{id}")
    public GameEventLog getGameEvent(@PathVariable Long id) {
        return gameEventLogRepository.findById(id).orElse(null);
    }

    @DeleteMapping("/game-events/{id}")
    public Map<String, String> deleteGameEvent(@PathVariable Long id) {
        gameEventLogRepository.deleteById(id);
        return Map.of("result", "success");
    }

    // ------------------ 叙事记忆 ------------------
    @GetMapping("/game-memories")
    public Page<GameNarrativeMemory> getGameMemories(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String memoryType,
            Pageable pageable) {
        Specification<GameNarrativeMemory> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (sessionId != null && !sessionId.isEmpty()) {
                predicates.add(cb.like(root.get("sessionId"), "%" + sessionId + "%"));
            }
            if (memoryType != null && !memoryType.isEmpty()) {
                predicates.add(cb.equal(root.get("memoryType"), memoryType));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return gameNarrativeMemoryRepository.findAll(spec, pageable);
    }

    @GetMapping("/game-memories/{id}")
    public GameNarrativeMemory getGameMemory(@PathVariable Long id) {
        return gameNarrativeMemoryRepository.findById(id).orElse(null);
    }

    @DeleteMapping("/game-memories/{id}")
    public Map<String, String> deleteGameMemory(@PathVariable Long id) {
        gameNarrativeMemoryRepository.deleteById(id);
        return Map.of("result", "success");
    }

    // ------------------ 恢复点 ------------------
    @GetMapping("/game-recovery-points")
    public Page<GameRecoveryPoint> getGameRecoveryPoints(
            @RequestParam(required = false) String sessionId,
            Pageable pageable) {
        Specification<GameRecoveryPoint> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (sessionId != null && !sessionId.isEmpty()) {
                predicates.add(cb.like(root.get("sessionId"), "%" + sessionId + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return gameRecoveryPointRepository.findAll(spec, pageable);
    }

    @GetMapping("/game-recovery-points/{id}")
    public GameRecoveryPoint getGameRecoveryPoint(@PathVariable Long id) {
        return gameRecoveryPointRepository.findById(id).orElse(null);
    }

    @DeleteMapping("/game-recovery-points/{id}")
    public Map<String, String> deleteGameRecoveryPoint(@PathVariable Long id) {
        gameRecoveryPointRepository.deleteById(id);
        return Map.of("result", "success");
    }

    // ------------------ 快照管理 ------------------
    @GetMapping("/game-snapshots")
    public Page<GameStateSnapshot> getGameSnapshots(
            @RequestParam(required = false) String sessionId,
            Pageable pageable) {
        Specification<GameStateSnapshot> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (sessionId != null && !sessionId.isEmpty()) {
                predicates.add(cb.like(root.get("sessionId"), "%" + sessionId + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return gameStateSnapshotRepository.findAll(spec, pageable);
    }

    @GetMapping("/game-snapshots/{id}")
    public GameStateSnapshot getGameSnapshot(@PathVariable Long id) {
        return gameStateSnapshotRepository.findById(id).orElse(null);
    }

    @DeleteMapping("/game-snapshots/{id}")
    public Map<String, String> deleteGameSnapshot(@PathVariable Long id) {
        gameStateSnapshotRepository.deleteById(id);
        return Map.of("result", "success");
    }

    @PutMapping("/game-snapshots/{id}/current")
    public Map<String, String> setCurrentSnapshot(@PathVariable Long id) {
        GameStateSnapshot snapshot = gameStateSnapshotRepository.findById(id).orElse(null);
        if (snapshot == null) {
            return Map.of("status", "error", "message", "快照不存在");
        }
        // 将同会话的其他快照设为非当前
        gameStateSnapshotRepository.updateCurrentFlag(snapshot.getSessionId());
        snapshot.setIsCurrent(true);
        gameStateSnapshotRepository.save(snapshot);
        return Map.of("status", "ok", "message", "已设为当前快照");
    }

    // ------------------ 角色特质管理 ------------------
    @GetMapping("/traits")
    public List<CharacterTraitDefinition> getAllTraits() {
        return traitRepository.findAll();
    }

    @GetMapping("/traits/{id}")
    public CharacterTraitDefinition getTrait(@PathVariable Long id) {
        return traitRepository.findById(id).orElse(null);
    }

    @PostMapping("/traits")
    public Map<String, Object> createTrait(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String traitName = request.get("traitName");
            String description = request.get("description");
            String example = request.get("example");

            if (traitName == null || traitName.trim().isEmpty()) {
                result.put("status", "error");
                result.put("message", "特质名称不能为空");
                return result;
            }

            if (traitRepository.existsByTraitName(traitName)) {
                result.put("status", "error");
                result.put("message", "特质名称已存在");
                return result;
            }

            CharacterTraitDefinition trait = new CharacterTraitDefinition();
            trait.setTraitName(traitName.trim());
            trait.setDescription(description != null ? description.trim() : "");
            trait.setExample(example != null ? example.trim() : "");

            traitRepository.save(trait);

            result.put("status", "ok");
            result.put("message", "特质创建成功");
            result.put("id", trait.getId());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PutMapping("/traits/{id}")
    public Map<String, Object> updateTrait(@PathVariable Long id, @RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            CharacterTraitDefinition trait = traitRepository.findById(id).orElse(null);
            if (trait == null) {
                result.put("status", "error");
                result.put("message", "特质不存在");
                return result;
            }

            String traitName = request.get("traitName");
            String description = request.get("description");
            String example = request.get("example");

            if (traitName != null && !traitName.trim().isEmpty()) {
                if (!traitName.equals(trait.getTraitName()) && traitRepository.existsByTraitName(traitName)) {
                    result.put("status", "error");
                    result.put("message", "特质名称已存在");
                    return result;
                }
                trait.setTraitName(traitName.trim());
            }
            if (description != null) trait.setDescription(description.trim());
            if (example != null) trait.setExample(example.trim());

            traitRepository.save(trait);

            result.put("status", "ok");
            result.put("message", "特质更新成功");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @DeleteMapping("/traits/{id}")
    public Map<String, Object> deleteTrait(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!traitRepository.existsById(id)) {
                result.put("status", "error");
                result.put("message", "特质不存在");
                return result;
            }
            traitRepository.deleteById(id);
            result.put("status", "ok");
            result.put("message", "特质删除成功");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }
}