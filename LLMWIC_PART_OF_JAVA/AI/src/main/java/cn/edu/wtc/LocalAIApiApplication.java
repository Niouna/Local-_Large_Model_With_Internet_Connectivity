package cn.edu.wtc;

import cn.edu.wtc.ollama.client.OllamaClient;
import cn.edu.wtc.ollama.config.OllamaConfig;
import cn.edu.wtc.manager.OllamaServiceManager;
import cn.edu.wtc.ollama.service.ChatService;
import cn.edu.wtc.ollama.service.SessionManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class LocalAIApiApplication {

    public static void main(String[] args) {
        // 启动双路 Ollama 服务（GPU + CPU）
        OllamaServiceManager.ensureAllRunning();
        SpringApplication.run(LocalAIApiApplication.class, args);
        System.out.println("✅ OpenAI Compatible API started at http://localhost:8080");
    }
    @EnableScheduling // <--- 别忘了加这个
    public class MemoryApplication {
        public static void main(String[] args) {
            SpringApplication.run(MemoryApplication.class, args);
        }
    }
    @Bean
    public OllamaConfig ollamaConfig() {
        OllamaConfig config = new OllamaConfig();
        config.loadFromFile();
        return config;
    }

    @Bean
    public OllamaClient ollamaClient(OllamaConfig config) {
        return new OllamaClient(config);
    }

    @Bean
    public SessionManager sessionManager() {
        return new SessionManager();
    }

    @Bean
    public ChatService chatService(OllamaClient client, SessionManager sessionManager) {
        return new ChatService(client, sessionManager);
    }
}
