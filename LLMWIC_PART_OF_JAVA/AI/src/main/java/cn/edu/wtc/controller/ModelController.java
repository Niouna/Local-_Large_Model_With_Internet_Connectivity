package cn.edu.wtc.controller;

import cn.edu.wtc.manager.OllamaServiceManager;
import cn.edu.wtc.ollama.config.OllamaConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api")
public class ModelController {

    private static final Logger log = LoggerFactory.getLogger(ModelController.class);
    private static final int GPU_PORT = 11434;
    private static final int CPU_PORT = 11435;

    @Autowired
    private OllamaConfig ollamaConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/models")
    public List<ModelInfo> getModels() {
        List<ModelInfo> result = new ArrayList<>();

        // 1. GPU 端口的所有模型（全部显示）
        Set<String> gpuModels = fetchModelNames(GPU_PORT);
        for (String model : gpuModels) {
            result.add(new ModelInfo(model, GPU_PORT, model + " (GPU)"));
        }

        // 2. CPU 端口模型：只显示配置中明确映射到 11435 的模型
        Map<String, Integer> portMap = ollamaConfig.getModelPortMap();
        Set<String> cpuAllowed = new HashSet<>();
        for (Map.Entry<String, Integer> entry : portMap.entrySet()) {
            if (entry.getValue() == CPU_PORT) {
                cpuAllowed.add(entry.getKey());
            }
        }

        Set<String> cpuModels = fetchModelNames(CPU_PORT);
        for (String model : cpuModels) {
            if (cpuAllowed.contains(model)) {
                result.add(new ModelInfo(model, CPU_PORT, model + " (CPU)"));
            }
        }

        // 按显示名称排序
        result.sort(Comparator.comparing(ModelInfo::getDisplayName));
        return result;
    }

    private Set<String> fetchModelNames(int port) {
        Set<String> names = new HashSet<>();
        if (!OllamaServiceManager.isServiceRunning(port)) {
            log.warn("Ollama 服务端口 {} 未运行", port);
            return names;
        }
        try {
            String json = OllamaServiceManager.getModels(port);
            JsonNode root = objectMapper.readTree(json);
            JsonNode modelsNode = root.get("models");
            if (modelsNode != null && modelsNode.isArray()) {
                for (JsonNode node : modelsNode) {
                    String name = node.get("name").asText();
                    names.add(name);
                }
            }
        } catch (Exception e) {
            log.error("解析端口 {} 模型列表失败", port, e);
        }
        return names;
    }

    public static class ModelInfo {
        private String name;
        private int port;
        private String displayName;

        public ModelInfo(String name, int port, String displayName) {
            this.name = name;
            this.port = port;
            this.displayName = displayName;
        }

        public String getName() { return name; }
        public int getPort() { return port; }
        public String getDisplayName() { return displayName; }
    }
}