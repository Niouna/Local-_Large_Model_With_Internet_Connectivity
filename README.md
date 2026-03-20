
Local-LLM-Internet-Connectivity (LLMWIC)

让本地大模型拥有“实时联网”与“专业数据查询”的能力

本项目是一个基于 Java (Spring Boot) 与 Python 混合架构的智能系统。它通过本地部署的大语言模型（如 DeepSeek-R1, Qwen2.5），结合 Python 强大的爬虫与数据处理生态，实现了突破模型训练时间限制的实时互联网搜索、天气查询及复杂网页信息提取。

核心功能

实时联网搜索：集成 Selenium 自动化浏览器，模拟真实用户行为搜索 Bing/Google，获取最新新闻、技术文档及实时资讯。
专业天气查询：内置中央气象局数据解析模块，支持通过省份/城市代码精准获取实时天气与预警信息。
RAG 增强对话：具备检索增强生成 (RAG) 能力，自动判断用户意图，将搜索结果作为上下文注入 Prompt，提升回答准确性。
长期记忆管理：内置 ConversationMemory 模块，支持多轮对话上下文记忆与快照调试。
双模式交互：
Web 管理后台：提供可视化的聊天界面与管理面板 (admin.html)。
CLI 命令行：支持终端交互式对话 (InteractiveChat)。
OpenAI 兼容接口：提供标准 OpenAI 格式 API (OpenAIController)，可轻松对接其他第三方客户端。

技术架构

本项目采用 前后端分离 与 多语言微服务协作 架构：

Java 后端 (核心控制层)
框架: Spring Boot
LLM 集成: 通过 OllamaClient 对接本地 Ollama 服务 (支持 DeepSeek-R1, Qwen2.5 等)。
HTTP 客户端: OkHttp (高性能网络请求)。
核心模块:
  IntentDetector: 用户意图识别。
  RAGOrchestrator: 检索增强生成编排。
  PythonSearchService: 负责调度 Python 子进程进行复杂爬取任务。
  ConversationMemory: 对话状态与历史管理。

Python 服务 (数据采集层)
核心库: Selenium (浏览器自动化), BeautifulSoup4 (HTML 解析), Requests。
功能模块:
  search_selenium.py: 通用搜索引擎爬虫，处理动态加载内容。
  weather_fetcher.py: 中央气象局数据专用采集器。
  city_matcher.py: 基于 province_code 的城市名称模糊匹配与代码映射。
  page_parser.py: 通用网页内容清洗与结构化提取。
依赖管理: 独立虚拟环境 (.venv)，避免污染系统环境。

前端展示
技术栈: HTML5, CSS3, Vanilla JavaScript。
特性: 响应式设计，支持 Markdown 渲染，实时流式输出。

项目结构

Local_Large_Model_With_Internet_Connectivity

├── LLMWIC_PART_OF_JAVA               # Java 后端主目录

│   ├── src/main/java/cn/edu/wtc

│   │   ├── cli                       # 命令行交互入口

│   │   ├── controller                # REST API 控制器 (Admin, OpenAI)

│   │   ├── ollama                    # Ollama 客户端封装

│   │   ├── relay                     # 搜索中继与意图识别

│   │   └── memory                    # 对话记忆管理

│   └── resources                     # 配置文件 (ollama.properties)

│
├── LLMWIC_PART_OF_PYTHON             # Python 数据采集主目录

│   ├── AI

│   │   ├── search_selenium.py        # 核心搜索脚本

│   │   ├── weather_fetcher.py        # 天气专用脚本

│   │   ├── city_matcher.py           # 城市代码匹配

│   │   ├── page_parser.py            # 页面解析器

│   │   ├── driver_manager.py         # WebDriver 管理 (Edge)

│   │   └── requirements.txt          # Python 依赖

│   ├── driver                        # 浏览器驱动 (edgedriver)

│   └── province_code                 # 省份/城市代码映射数据

│

└── README.md
快速开始

前置要求
JDK 17+ 已安装并配置环境变量。
Python 3.8+ 已安装。
Ollama 已安装并运行，且拉取了所需模型：
      ollama serve
   ollama pull deepseek-r1:7b
   ollama pull qwen2.5:3b
   
Edge 浏览器 已安装 (用于 Python 爬虫)。

配置 Python 环境
进入 Python 目录并安装依赖：
cd LLMWIC_PART_OF_PYTHON/AI

创建虚拟环境 (推荐)
python -m venv .venv

激活环境
Windows:
.venvScriptsactivate
Mac/Linux:
source .venv/bin/activate

安装依赖 (如果没有 requirements.txt，请手动安装以下核心库)
pip install selenium beautifulsoup4 requests flask lxml

注意：确保 driver 文件夹中的 edgedriver 版本与本地 Edge 浏览器版本一致。

配置 Java 后端
编辑 LLMWIC_PART_OF_JAVA/src/main/resources/ollama.properties：
ollama.baseUrl=http://127.0.0.1:11434
ollama.defaultModel=deepseek-r1:7b
如需多模型负载，可配置端口映射
ollama.modelPorts=deepseek-r1:7b=11434, qwen2.5:3b=11435

启动服务

第一步：启动 Python 服务 (如需独立服务模式)(注：根据当前代码，Java 可能直接通过 ProcessBuilder 调用 Python 脚本，若如此可跳过此步，确保 Python 环境可用即可)
python search_selenium.py 
或其他入口脚本，视具体调用方式而定

第二步：启动 Java 应用
cd LLMWIC_PART_OF_JAVA
使用 Maven (如果有 pom.xml)
mvn spring-boot:run
或者直接运行主类
java -jar target/your-app.jar

访问系统
Web 管理后台: 打开浏览器访问 http://localhost:8080/admin (端口视配置而定)。
命令行交互: 运行 cn.edu.wtc.cli.InteractiveChat 类。

关键模块说明
模块   文件   描述
意图识别   IntentDetector.java   分析用户输入，决定是直接回答还是触发搜索/天气查询。

搜索调度   PythonSearchService.java   Java 调用 Python 脚本的桥梁，传递查询词并接收 JSON 结果。

动态爬虫   search_selenium.py   使用 Headless Edge 浏览器执行搜索，绕过简单的反爬机制。

天气专有   weather_fetcher.py   针对中央气象局网站结构的定向抓取，利用 province_code 加速定位。

记忆管理   ConversationMemory.java   存储对话历史，支持快照调试 (MemoryDebugSnapshotService)。

高级配置与已知问题

设备兼容性与硬件建议
硬件门槛：运行 7B 或以上参数模型需要足够的 GPU 显存 (建议 8GB+)，否则推理速度会显著下降。
作者环境参考：本项目在 Intel 酷睿 i7-12700H + ARC A730m (12G) 环境下经过充分测试。
移植建议：由于 Intel GPU 生态的特殊性，若想在其他设备稳定运行双模型并发，建议至少配置 2 张以上独立显卡 或使用 NVIDIA 显卡以获得最佳兼容性。当然，愿意折腾 Intel 显卡的用户也可以正常运行，只需参考下方的特别提示。

针对 Intel GPU (iGPU/dGPU) 用户的特别提示
在使用 Ollama Intel 优化版本 同时运行多个模型（例如 deepseek-r1:7b + qwen2.5:3b）时，可能会遇到小模型无法加载或推理卡死的问题。

原因分析：
  这是由于不同模型对显存/内存的需求不同，且 Intel 后端在多模型并发时，若检测到设备性能不一致或显存碎片化，可能导致调度失败。
  
解决方案：
  建议强制屏蔽 GPU 检测，将所有模型流量统一引导至 CPU 运行（虽然 Ollama 日志可能仍显示 gpu，但实际计算已 fallback 到 CPU，从而保证稳定性）。

操作步骤：
  在启动 Ollama 服务前，设置以下环境变量：

  Windows (PowerShell):
    $env:OLLAMA_NO_GPU="1"
  ollama serve
  

  Windows (CMD):
    set OLLAMA_NO_GPU=1
  ollama serve
  

  Linux / Mac:
    export OLLAMA_NO_GPU=1
  ollama serve
  

  >注：此配置会牺牲部分推理速度，但能确保多模型并发时的系统稳定性。如果你的显存充足（>16GB）且仅运行单一大模型，可尝试移除此变量以启用 GPU 加速。

注意事项

浏览器驱动: 项目默认使用 Edge Driver。如果更新 Edge 浏览器，请同步更新 driver/edgedriver_win64 下的驱动文件，否则爬虫将失败。
反爬策略: 频繁搜索可能会触发搜索引擎验证码。生产环境建议增加随机延时或接入官方 Search API。
隐私安全: 本项目完全本地运行，不上传任何对话数据到云端，适合处理敏感信息。

📄 许可证
MIT License
