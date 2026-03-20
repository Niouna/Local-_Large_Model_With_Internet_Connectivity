package cn.edu.wtc.ollama.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class OllamaConfig {

    // 默认配置值
    private String baseUrl = "http://127.0.0.1:11434";
    private String defaultModel = "deepseek-r1:7b";
    private int connectTimeout = 30;
    private int readTimeout = 300;
    private int writeTimeout = 30;
    private boolean enableLogging = true;
    private String logLevel = "INFO";

    // 模型到端口的映射
    private Map<String, Integer> modelPortMap = new HashMap<>();

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static ObjectMapper objectMapper;

    private String configFilePath = "D:\\个人项目\\Local _Large_Model_With_Internet_Connectivity\\LLMWIC_PART_OF_JAVA\\AI\\src\\main\\java\\cn\\edu\\wtc\\ollama.properties";

    public OllamaConfig() {}

    public void loadFromFile() {
        try (InputStream input = new FileInputStream(configFilePath)) {
            Properties prop = new Properties();
            prop.load(input);

            this.baseUrl = prop.getProperty("ollama.baseUrl", this.baseUrl);
            this.defaultModel = prop.getProperty("ollama.defaultModel", this.defaultModel);
            this.connectTimeout = Integer.parseInt(prop.getProperty("ollama.connectTimeout", String.valueOf(this.connectTimeout)));
            this.readTimeout = Integer.parseInt(prop.getProperty("ollama.readTimeout", String.valueOf(this.readTimeout)));
            this.writeTimeout = Integer.parseInt(prop.getProperty("ollama.writeTimeout", String.valueOf(this.writeTimeout)));
            this.enableLogging = Boolean.parseBoolean(prop.getProperty("ollama.enableLogging", String.valueOf(this.enableLogging)));
            this.logLevel = prop.getProperty("ollama.logLevel", this.logLevel);

            // 解析模型端口映射
            String modelPortsStr = prop.getProperty("ollama.modelPorts", "");
            if (!modelPortsStr.isBlank()) {
                String[] entries = modelPortsStr.split(",");
                for (String entry : entries) {
                    String[] kv = entry.trim().split("=");
                    if (kv.length == 2) {
                        String model = kv[0].trim();
                        int port = Integer.parseInt(kv[1].trim());
                        modelPortMap.put(model, port);
                    }
                }
            }

            System.out.println("从配置文件加载配置完成: " + configFilePath);
        } catch (IOException e) {
            System.out.println("配置文件 " + configFilePath + " 不存在，使用默认配置");
        }
    }

    public void loadFromEnv() {
        String url = System.getenv("OLLAMA_BASE_URL");
        if (url != null && !url.trim().isEmpty()) {
            this.baseUrl = url;
        }
        String model = System.getenv("OLLAMA_DEFAULT_MODEL");
        if (model != null && !model.trim().isEmpty()) {
            this.defaultModel = model;
        }
        String timeout = System.getenv("OLLAMA_READ_TIMEOUT");
        if (timeout != null && !timeout.trim().isEmpty()) {
            this.readTimeout = Integer.parseInt(timeout);
        }
    }

    public static ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
        return objectMapper;
    }

    // 新增方法：根据模型名获取对应的基础 URL
    public String getBaseUrlForModel(String model) {
        if (modelPortMap.containsKey(model)) {
            int port = modelPortMap.get(model);
            return "http://127.0.0.1:" + port;
        }
        return baseUrl; // 如果没有映射，使用默认 baseUrl
    }

    // ============== Getter 和 Setter 方法 ==============
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
    public int getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }
    public int getReadTimeout() { return readTimeout; }
    public void setReadTimeout(int readTimeout) { this.readTimeout = readTimeout; }
    public int getWriteTimeout() { return writeTimeout; }
    public void setWriteTimeout(int writeTimeout) { this.writeTimeout = writeTimeout; }
    public boolean isEnableLogging() { return enableLogging; }
    public void setEnableLogging(boolean enableLogging) { this.enableLogging = enableLogging; }
    public String getLogLevel() { return logLevel; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }
    public String getConfigFilePath() { return configFilePath; }
    public void setConfigFilePath(String configFilePath) { this.configFilePath = configFilePath; }

    public void printConfig() {
        System.out.println("=== Ollama 客户端配置 ===");
        System.out.println("服务地址: " + baseUrl);
        System.out.println("默认模型: " + defaultModel);
        System.out.println("连接超时: " + connectTimeout + "秒");
        System.out.println("读取超时: " + readTimeout + "秒");
        System.out.println("写入超时: " + writeTimeout + "秒");
        System.out.println("启用日志: " + enableLogging);
        System.out.println("日志级别: " + logLevel);
        System.out.println("配置文件: " + configFilePath);
        System.out.println("模型端口映射: " + modelPortMap);
        System.out.println("=========================");
    }
}