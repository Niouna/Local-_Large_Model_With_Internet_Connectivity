智能搜索与天气查询系统技术文档
项目概述
本项目是一个基于 Python 的智能搜索系统，支持网页搜索、内容抓取、信息提取和天气查询。系统采用模块化设计，包含搜索模块、天气模块和工具模块三大部分，专为配合 Java RAG 系统提供高质量的实时数据源。
核心能力：
智能搜索：支持 Bing/百度/duckduckgo，自动识别 AI 摘要，去重过滤广告。
深度提取：针对硬件、汽车、游戏等垂直领域进行结构化信息提取。
天气查询：支持全国城市精确匹配（含别名/拼音），抓取中央气象台实时数据。
LLM 友好：输出标准化的 summary_for_llm 和结构化 JSON，便于大模型理解。

一、项目结构
AI/
├── search_module/          # 搜索核心模块
│   ├── search_api.py       # 基础搜索 API
│   ├── enhanced_search_api.py  # 增强搜索 API（支持正文抓取）
│   ├── search_engine.py    # 搜索引擎封装
│   ├── filters.py          # 结果过滤
│   ├── quality_scorer.py   # 质量评分
│   ├── info_extractor.py   # 通用信息提取
│   ├── hardware_extractor.py   # 硬件专用提取
│   ├── car_extractor.py    # 汽车专用提取
│   ├── game_extractor.py   # 游戏专用提取
│   ├── info_classifier.py  # 信息分类
│   ├── validator.py        # 交叉验证
│   └── whitelist_config.py # 白名单配置
├── weather_module/         # 天气查询模块
│   ├── weather_api.py      # 天气 API 入口
│   ├── city_matcher.py     # 城市匹配
│   ├── data_loader.py      # 数据加载
│   ├── page_parser.py      # 页面解析
│   ├── weather_fetcher.py  # 数据抓取
│   └── formatter.py        # 结果格式化
├── utils/                  # 工具模块
│   ├── driver_manager.py   # 浏览器驱动管理
│   └── config.py           # 全局配置
├── connection_keep_alive_module/
│   └── search_service.py   # Flask HTTP 服务 (Java 调用入口)
└── driver/                 # Edge 驱动目录
└── edgedriver_win64/
└── msedgedriver.exe

二、快速开始
2.1 环境要求
Python 3.8+
Microsoft Edge 浏览器
Edge WebDriver（与浏览器版本匹配）
依赖包：selenium, flask, beautifulsoup4, requests
2.2 基础配置
在 utils/config.py 中配置：
class Config:
BROWSER = "edge"           # 浏览器类型
HEADLESS = False           # 是否无头模式 (建议 False 以便调试)
IMPLICIT_WAIT = 5          # 隐式等待时间（秒）
2.3 驱动配置
在 utils/driver_manager.py 中设置驱动路径：
DRIVER_PATH = Path(r"你的绝对路径\msedgedriver.exe")

三、搜索模块 API 详解
3.1 基础搜索 API (search_api.py)
SearchAPI 类
初始化
from search_module import SearchAPI
from utils.driver_manager import DriverManager
api = SearchAPI(
engine="bing",              # 搜索引擎：bing/baidu/duckduckgo
driver_manager=DriverManager
)
核心方法：search(query, max_results=3)
result = api.search(
query="iPhone 16 评测",      # 搜索关键词
max_results=3                # 返回结果数量
)
返回结构：
{
"query": "搜索关键词",
"search_engine": "bing",
"search_time": "2026-02-22T10:30:00",
"elapsed_seconds": 2.5,
"result_count": 3,
"has_ai_overview": True,           # 是否有 Bing AI 摘要
"ai_overview_count": 1,
"results": [
{
"title": "标题",
"link": "https://...",
"snippet": "摘要内容",
"is_ai_overview": False,
"quality_score": 0.85,       # 质量分数 0-1.5
"trust_level": "high",       # 可信度：high/medium/low
"key_facts": {               # 提取的关键信息
"products": ["iPhone 16"],
"prices": ["5999 元"],
"dates": ["2024 年 9 月"],
"data": [],
"recommendations": ["值得购买"]
},
"info_fragments": [          # 信息片段
{"type": "product", "content": "iPhone 16"},
{"type": "price", "content": "5999"}
],
"info_type": {               # 信息类型
"is_fact": True,
"is_opinion": False,
"is_recommendation": True,
"has_data": True,
"uncertainty_level": "low"
}
}
],
"cross_validation": {                # 交叉验证结果
"consistent_facts": ["多来源提及：iPhone 16"],
"conflicting_facts": [],
"confidence": "high",            # 置信度：high/medium/low
"source_count": 3
},
"summary_for_llm": "格式化摘要...",  # 【关键】给 LLM 的摘要
"note_for_llm": "给 AI 的备注...",
"status": "success"
}
便捷函数
from search_module import search
result = search(
query="搜索词",
max_results=3,
engine="bing",
driver_manager=DriverManager
)

3.2 增强搜索 API (enhanced_search_api.py)
EnhancedSearchAPI 类
继承自 SearchAPI，新增白名单正文抓取功能，适合深度问答。
初始化
from search_module import EnhancedSearchAPI
api = EnhancedSearchAPI(
engine="bing",
driver_manager=DriverManager,
weather_data_dir="天气数据目录"  # 可选，天气模块用
)
核心方法：search(query, max_results=3, fetch_content=True, content_max_length=3000)
result = api.search(
query="小米 SU7 续航测试",
max_results=3,
fetch_content=True,           # 是否抓取正文
content_max_length=3000       # 正文最大长度
)
增强返回字段：
{
# ... 基础字段相同 ...
"enhanced_info": {
"content_fetched_count": 2,      # 成功抓取数量
"total_results": 3,
"fetch_time": "2026-02-22T10:30:00",
"query_type": "car"              # 查询类型：hardware/car/game/general
},
"results": [
{
# ... 基础字段 ...
"content_fetched": True,      # 是否抓取成功
"content_length": 2500,       # 正文长度
"full_content": "完整正文...", # 抓取的正文内容
"extractor_type": "car",      # 使用的提取器
"key_facts_enhanced": {       # 增强后的关键信息
"products": ["小米 SU7"],
"prices": ["21.59 万元"],
"specs": ["700km 续航", "73.6kWh 电池"],
"brands": ["小米"],
"scores": ["4.8 分"]
},
"info_fragments_enhanced": [  # 增强信息片段
{"type": "product", "content": "小米 SU7"},
{"type": "price", "content": "21.59 万"}
]
}
]
}
自动查询类型检测：
硬件类：包含 "CPU", "显卡", "RTX", "Intel", "AMD" 等关键词
汽车类：包含 "比亚迪", "特斯拉", "续航", "新能源" 等关键词
游戏类：包含 "原神", "崩铁", "配队", "攻略" 等关键词
便捷函数
from search_module import search_enhanced
result = search_enhanced(
query="搜索词",
max_results=3,
fetch_content=True,
driver_manager=DriverManager,
weather_data_dir=None
)

3.3 信息提取器
通用提取器 (info_extractor.py)
from search_module.info_extractor import extract_info_fragments, extract_key_facts
fragments = extract_info_fragments(text="iPhone 16 售价 5999 元...")
facts = extract_key_facts(snippet="摘要内容", title="文章标题")
硬件专用提取器 (hardware_extractor.py)
from search_module.hardware_extractor import extract_hardware_info, merge_with_original
hw_info = extract_hardware_info(text="正文内容", source="https://zol.com.cn/...")
# 提取 products, prices, specs, scores, recommendations
汽车专用提取器 (car_extractor.py)
from search_module.car_extractor import extract_car_info, is_car_query
if is_car_query("比亚迪汉 EV 续航"):
car_info = extract_car_info(text, source_url)
# 提取 products, prices, specs (续航/电池), brands, scores
游戏专用提取器 (game_extractor.py)
from search_module.game_extractor import extract_game_info, is_game_query
if is_game_query("原神配队推荐"):
game_info = extract_game_info(text, source, query)
# 提取 characters, teams, weapons, ratings, stages, strategies

3.4 辅助模块
质量评分 (quality_scorer.py): calculate_quality_score(), get_domain_trust_level() (区分知乎/官网/营销号)。
结果过滤 (filters.py): filter_results() (去广告), remove_duplicates() (基于相似度)。
信息分类 (info_classifier.py): classify_info_type() (区分事实/观点/推荐)。
交叉验证 (validator.py): cross_validate() (多源信息一致性检查)。

四、天气模块 API 详解
4.1 天气查询 API (weather_api.py)
WeatherAPI 类
初始化
from weather_module import create_weather_api
weather_api = create_weather_api(
data_dir=r"D:\...\province_code",  # 省份代码数据目录
driver_manager=DriverManager,
style="default"                     # 输出风格：default/simple/detailed/json
)
核心方法：query(city_query)
result = weather_api.query("武汉")
# 或
result = weather_api.query("江城")  # 支持别名
返回结构：
{
"success": True,
"city": "武汉",
"province": "湖北省",
"match_type": "exact",           # 匹配类型：exact/pinyin/alias/fuzzy
"formatted": "格式化天气文本...", # 【关键】给 LLM 的格式化结果
"data": ParsedWeather(...),      # 原始数据对象
"source_url": "https://www.nmc.cn/..."
}
快速查询
text = weather_api.quick_query("北京")
# 直接返回格式化文本，失败返回错误信息
4.2 城市匹配 (city_matcher.py)
支持多种匹配方式：
精确匹配: "武汉"
拼音: "wuhan"
英文: "beijing"
别名: "江城" (武汉), "帝都" (北京), "魔都" (上海)
缩写: "wh", "bj", "sh"
matcher.add_alias("武汉", "热干面之都")  # 动态添加别名
4.3 其他组件
数据加载 (data_loader.py): 加载省份/城市代码映射。
页面解析 (page_parser.py): 解析中央气象台 HTML，提取实况、7 天预报、24 小时预报、预警。
数据抓取 (weather_fetcher.py): 处理动态加载等待。
结果格式化 (formatter.py): 生成自然语言描述或 JSON。

五、工具模块 API 详解
5.1 驱动管理 (driver_manager.py)
from utils.driver_manager import DriverManager
# 检查驱动
exists, msg, path = DriverManager.check_edge_driver()
# 获取驱动实例
driver = DriverManager.get_driver()
# 使用完成后必须手动关闭
driver.quit()

六、HTTP 服务 API (Java 调用入口)
这是 Java RAG 系统 (PythonSearchService.java) 调用的核心接口。
6.1 启动服务
python connection_keep_alive_module/search_service.py [端口]
# 默认端口 5000
6.2 接口端点
POST /search - 执行搜索 (主要接口)
请求体：
{
"query": "搜索关键词",
"max_results": 5,
"fetch_content": true
}
响应：
{
"query": "搜索关键词",
"status": "success",
"result_count": 3,
"results": [...],
"summary_for_llm": "...",  // Java 端直接取此字段喂给大模型
"elapsed_seconds": 3.5
}
GET /health - 健康检查
响应：
{
"status": "ok",
"service": "search-enhanced",
"driver_ready": true
}

七、完整使用示例
示例 1：基础搜索
from search_module import search
from utils.driver_manager import DriverManager
result = search(query="2026 年法定节假日安排", max_results=3, driver_manager=DriverManager)
print(result['summary_for_llm'])
示例 2：增强搜索（硬件）
from search_module import search_enhanced
result = search_enhanced(
query="RTX 5080 评测 价格",
max_results=3,
fetch_content=True,
driver_manager=DriverManager
)
for r in result['results']:
if r.get('content_fetched'):
print(f"产品：{r['key_facts_enhanced']['products']}")
print(f"价格：{r['key_facts_enhanced']['prices']}")
示例 3：天气查询
from weather_module import create_weather_api
weather_api = create_weather_api(data_dir=r"...\province_code", driver_manager=DriverManager)
result = weather_api.query("上海")
if result['success']:
print(result['formatted'])
示例 4：集成到 LLM 应用 (模拟 Java 逻辑)
class LLMSearchTool:
def __init__(self):
self.weather_api = create_weather_api(data_dir=r"...\province_code", driver_manager=DriverManager)
def query(self, user_input: str) -> str:
# 简单关键词判断 (Java 端由 IntentDetector 完成)
if any(kw in user_input for kw in ["天气", "温度", "预报"]):
return self.weather_api.quick_query(user_input)
else:
result = search_enhanced(query=user_input, max_results=3, fetch_content=True, driver_manager=DriverManager)
return result['summary_for_llm']

八、注意事项
1.驱动版本匹配：Edge 浏览器和 WebDriver 版本必须一致，否则报错 SessionNotCreatedException。
2.反爬策略：仅抓取 whitelist_config.py 中的域名，避免触发反爬或抓取低质内容。
3.资源释放：Flask 服务中长期运行需确保 Driver 复用或正确关闭，避免内存泄漏。
4.超时处理：网络不稳定时适当调整 IMPLICIT_WAIT 和 timeout 参数。
5.数据更新：天气省份代码数据需定期从中央气象台网站更新以保持城市列表最新。

九、错误处理
错误	原因	解决方案
DriverNotFoundError	驱动文件不存在	检查 DRIVER_PATH 路径
SessionNotCreatedException	驱动版本不匹配	更新 Edge 和 WebDriver
抓取内容为空	页面动态加载失败	增加等待时间或检查白名单配置
城市匹配失败	城市名错误或数据缺失	使用拼音或别名重试

版本：v1.0 (Python Search & Weather Module)
更新日期：2026-02-05