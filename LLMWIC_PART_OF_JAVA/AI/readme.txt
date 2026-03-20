Local AI API 项目文档
1. 项目概述
Local AI API 是一个基于Java Spring Boot和Ollama的高性能本地 AI 服务封装。它不仅提供标准的 OpenAI 兼容 API，还内置了智能 RAG（检索增强生成）引擎和双路 Ollama 实例管理，确保流畅的对话体验。
核心功能包括：
双路服务架构：自动管理两个 Ollama 实例。
GPU 实例 (Port 11434)：运行大模型（如 DeepSeek-R1），负责复杂推理和最终回答。
CPU 实例 (Port 11435)：运行小模型（如 Qwen2.5-3B），负责快速意图识别和路由，避免阻塞主线程。
智能联网搜索 (RAG)：系统会自动分析用户问题，若涉及实时信息，自动调用 Python 搜索服务，并将结果喂给大模型生成准确答案。
OpenAI 兼容接口：提供/v1/chat/completions端点，可直接对接任何支持 OpenAI 格式的客户端。
多端支持：
Web 界面：现代化的聊天 UI (index.html)，支持一键开启/关闭联网模式，展示思考过程。
CLI 命令行：支持交互式对话、会话管理和模型测试。
API 服务：后台静默运行，供其他程序调用。
会话管理：支持多会话、历史记录、token 估算。
2. 技术栈
Java 21
Spring Boot 3.2.5(Web, Validation)
OkHttp(高性能 HTTP 客户端)
Jackson(JSON 处理)
Maven(构建工具)
JUnit(测试)
外部依赖:
Ollama: 本地大模型运行时 (需安装并配置双实例)
Python Search Service: 独立的 Python 脚本 (端口 5000)，负责 Selenium/Bing 搜索
3. 项目结构
text
编辑
src/main/java/cn/edu/wtc├── LocalAIApiApplication.java   # Spring Boot 启动类 (API 模式)
├── Main.java                    # 命令行启动类 (CLI 模式)
├── config
│   └── OllamaConfig.java        # 配置加载 (支持模型端口映射)
├── manager
│   └── OllamaServiceManager.java # 【核心】双路 Ollama 进程管理器 (启停/监控)
├── client
│   └── OllamaClient.java        # Ollama HTTP 客户端 (动态路由端口)
├── service
│   ├── ChatService.java         # 聊天业务逻辑 (会话管理)
│   ├── SessionManager.java      # 内存会话存储
│   └── ...
├── relay                        # 【新增】RAG 与搜索中继层
│   ├── orchestrator
│   │   └── RAGOrchestrator.java # 【核心】RAG 编排器 (意图判断 + 结果融合)
│   ├── detector
│   │   └── IntentDetector.java  # 关键词/语义意图检测
│   └── search
│   │   ├── SearchService.java   # 搜索接口
│   │   ├── PythonSearchService.java # 调用 Python 搜索服务实现
│   │   └── WebSearchService.java  # 网页搜索逻辑
├── controller
│   └── OpenAIController.java    # REST API 控制器 (/v1/chat/completions)
├── cli
│   ├── InteractiveChat.java     # 命令行交互逻辑
│   └── ...
└── dto                          # 数据传输对象 (Request/Response)
(前端文件index.html,style.css,script.js位于 resources/static 或项目根目录)
4. 详细说明
4.1 config 包
OllamaConfig
职责：加载和管理所有配置（properties 文件、环境变量），新增支持模型与端口的动态映射。
关键方法：
loadFromFile()：从ollama.properties加载配置。
loadFromEnv()：从环境变量覆盖配置。
getObjectMapper()：返回单例 Jackson ObjectMapper。
getBaseUrlForModel(model)：新增，根据模型名称返回对应的端口 URL（如qwen2.5:3b->http://127.0.0.1:11435）。
printConfig()：打印当前配置及双路状态。
配置属性（ollama.properties示例）：
properties
编辑
ollama.baseUrl=http://127.0.0.1:11434ollama.defaultModel=deepseek-r1:7b# 模型端口映射 (格式：model_name:port,model_name:port)ollama.modelPorts=qwen2.5:3b=11435,deepseek-r1:7b=11434ollama.connectTimeout=30ollama.readTimeout=300ollama.writeTimeout=30ollama.enableLogging=trueollama.logLevel=INFO
4.2 client 包
OllamaClient
职责：封装与 Ollama API 的 HTTP 通信，支持动态切换目标端口。
关键方法：
OllamaClient(OllamaConfig config)：构造客户端，内部构建 OkHttpClient。
String chat(String model, List<Map<String,String>> messages)：增强，自动根据model查找对应端口发送/api/chat请求。
String generate(String model, String prompt)：增强，自动根据model查找对应端口发送/api/generate请求。
boolean healthCheck()：调用/api/tags检查默认服务是否可用。
String listModels()：获取模型列表。
使用规范：不直接管理会话，仅作为 HTTP 工具。端口路由由OllamaConfig决定。
4.3 model 包
Conversation
职责：表示一个对话会话，存储历史消息、模型、token 估算。
内部类Message：每条消息包含 role、content、timestamp。
关键方法：
addUserMessage(content)/addAssistantMessage(content)：添加消息，自动管理历史长度。
getHistoryForApi()：返回适合 Ollama API 格式的历史列表。
getRecentHistory(n)：获取最近 n 条消息。
clearHistory()、getSummary()、printHistory()。
使用规范：每个会话对应一个Conversation实例，由SessionManager管理。
4.4 service 包
SessionManager
职责：管理所有活跃会话的存储（内存）。
关键方法：
getOrCreateSession(sessionId, model)：获取或创建会话。
getSession(sessionId)、endSession(sessionId)、listSessions()。
ChatService
职责：提供高层聊天接口，协调客户端、会话管理。
关键方法：
chat(model, message)：单次对话（无历史）。
chatWithSession(sessionId, model, message)：连续对话（使用会话）。
getSession(sessionId)、endSession(sessionId)、listSessions()。
(注：RAG 逻辑已移至RAGOrchestrator)
使用规范：控制器和 CLI 均通过此服务调用基础对话，RAG 场景由 Orchestrator 协调。
IntentDetector(已实现)
职责：判断用户消息是否需要触发联网搜索。
方法：
needsWebSearch(message)：基于关键词（如“联网搜索”、“最新”）返回布尔值。
extractQuery(message)：移除触发词，得到干净查询。
PythonSearchService(已实现)
职责：调用 Python 搜索脚本，返回结构化结果。
方法：
search(query, maxResults)：POST 请求到http://127.0.0.1:5000/search，解析 JSON 返回summary_for_llm。
4.5 relay 包 (新增核心模块)
RAGOrchestrator
职责：RAG 流程的总指挥，自主判断是否需要联网。
工作流程：
接收用户消息。
调用chat("qwen2.5:3b", prompt)(自动路由到 CPU 端口 11435) 进行快速意图判断。
若需联网，调用SearchService获取搜索结果。
将“用户问题 + 搜索摘要”组装成 Prompt，发送给ChatService(使用 GPU 端口 11434 的大模型)。
返回最终结果。
关键方法：
chatWithRag(model, message)：单次对话 + RAG。
chatWithRag(sessionId, model, message)：连续对话 + RAG。
4.6 controller 包
OpenAIController
职责：提供/v1/chat/completions端点，完全兼容 OpenAI API。
逻辑：
接收请求，提取最后一条用户消息。
委托给RAGOrchestrator.chatWithRag()处理（自动包含意图判断和搜索逻辑）。
构造标准 OpenAI 响应返回。
使用规范：仅做参数解析和响应组装，业务逻辑委托给RAGOrchestrator。
4.7 cli 包
InteractiveChat
职责：命令行交互界面，提供三种模式。
模式：
单次对话：无历史，每次独立。
连续对话：使用会话保持历史。
测试不同模型：允许手动指定模型。
命令：/history、/clear、/model、/sessions、/summary、/quit。
Main
职责：命令行入口。
逻辑：
调用OllamaServiceManager.ensureAllRunning()启动双路服务。
初始化核心组件。
启动InteractiveChat。
4.8 manager 包 (核心升级)
OllamaServiceManager
职责：全权管理双路 Ollama 后台进程。
关键逻辑：
启动策略：
启动GPU 实例(端口 11434)，设置环境变量ONEAPI_DEVICE_SELECTOR(针对 Intel Arc) 以启用加速。
启动CPU 实例(端口 11435)，设置OLLAMA_NUM_GPU=0强制 CPU 推理，用于运行小模型。
健康检查：分别检测两个端口是否就绪。
资源隔离：确保两个实例互不干扰，避免显存冲突。
关键方法：
ensureAllRunning()：确保双路服务都在运行。
stopAllServices()：停止双路服务。
isServiceRunning(port)：检测指定端口。
getAllStatus()：获取双路状态字符串。
viewLog(instanceName)：查看指定实例日志。
使用规范：所有需要 Ollama 服务的模块（Main、LocalAIApiApplication）都应先调用ensureAllRunning()。
4.9 util 包
TokenEstimator
职责：粗略估算 token 数量（字符数/2）。
方法：
estimateTokens(text)。
4.10 dto 包
ChatCompletionRequest
字段：model、messages、temperature、maxTokens、stream。
内部类Message。
ChatCompletionResponse
字段：id、object、created、model、choices、usage。
内部类Choice、Message、Usage。
4.11 前端资源 (新增)
index.html: 聊天界面，包含消息列表、输入框、联网开关。
style.css: 现代化样式，支持深色模式，美化思考过程显示。
script.js: 处理 API 请求，解析响应中的<think>标签并分离显示思考过程和最终答案。
5. 配置说明
配置文件位于src/main/java/cn/edu/wtc/ollama.properties，可自定义：
ollama.baseUrl：Ollama 默认服务地址（默认http://127.0.0.1:11434）。
ollama.defaultModel：默认模型（如deepseek-r1:7b）。
ollama.modelPorts：关键配置，定义模型与端口的映射关系，实现双路分流（例：qwen2.5:3b=11435,deepseek-r1:7b=11434）。
ollama.connectTimeout/readTimeout/writeTimeout：超时设置。
ollama.enableLogging/logLevel：日志开关和级别。
注意：Python 搜索服务地址硬编码在PythonSearchService.java中 (http://127.0.0.1:5000)，如需修改请调整该类。
6. 如何运行
前置要求
安装Ollama。
拉取模型：
bash
编辑
ollama pull deepseek-r1:7b   # 用于主推理 (GPU)ollama pull qwen2.5:3b       # 用于意图识别 (CPU)
(可选) 启动 Python 搜索服务。
6.1 命令行模式
直接运行cn.edu.wtc.cli.Main：
自动启动双路 Ollama 服务（若未运行）。
显示菜单，选择交互模式（单次/连续/测试模型）。
6.2 API 服务模式
运行cn.edu.wtc.LocalAIApiApplication（Spring Boot 入口）：
自动启动双路 Ollama 服务。
服务监听http://localhost:8080，提供/v1/chat/completions端点。
可配合index.html使用 Web 界面。
6.3 构建与打包
bash
编辑
mvn clean package# 运行 API 服务java -cp target/AI-1.0-SNAPSHOT.jar cn.edu.wtc.LocalAIApiApplication# 运行 CLIjava -cp target/AI-1.0-SNAPSHOT.jar cn.edu.wtc.cli.Main
7. 测试
7.1 单元测试（待补充）
建议为OllamaClient、Conversation、SessionManager编写单元测试，使用 Mock 隔离外部依赖。
7.2 集成测试
手动启动服务后，使用curl或 Postman 测试/v1/chat/completions接口。
测试联网搜索功能：发送包含“联网搜索”关键词的请求，观察是否触发 Python 服务调用。
使用DialogueLoggerTest(如有) 记录多轮对话，验证会话管理效果。
8. 扩展与自定义
8.1 调整双路策略
修改OllamaServiceManager.java中的启动参数（如显存限制、设备选择器、端口号）以适应不同硬件。
8.2 优化意图识别
在IntentDetector.java中增加更多关键词，或在RAGOrchestrator.java中优化小模型的 Prompt 以提高判断准确率。
8.3 更换搜索源
修改PythonSearchService.java中的 API 调用逻辑，对接 Bing API 或其他搜索引擎，或修改 Python 脚本本身。
8.4 支持流式输出
修改OpenAIController和RAGOrchestrator，当request.stream == true时，使用Flux返回 SSE 流，实现打字机效果。
8.5 持久化会话
将SessionManager的会话存储改为数据库（如 SQLite）或文件，实现重启后恢复历史对话。
8.6 上下文压缩
在Conversation.getHistoryForApi()中实现滑动窗口或摘要算法，避免 token 无限增长导致显存溢出。

版本：v2.0 (Dual-Instance & RAG Enabled)
更新日期：2026-02-05