# config.py
"""搜索模块配置文件"""

# 域名信誉分级
DOMAIN_TRUST_LEVELS = {
    "high": [
        "gov.cn", "edu.cn", "ac.cn",
        "zhihu.com", "wikipedia.org",
        "mydrivers.com", "hangge.com", "csdn.net",
        "github.com", "stackoverflow.com",
        "microsoft.com", "nvidia.com", "amd.com"
    ],
    "medium": [
        "juejin.cn", "segmentfault.com",
        "cnblogs.com", "51cto.com",
        "baidu.com", "bing.com"
    ],
    "low": [
        "hao123.com", "2345.com",
        "baijiahao.com", "toutiao.com"
    ]
}

# 广告关键词
AD_KEYWORDS = [
    "广告", "推广", "赞助", "Sponsored", "Ad",
    "立即购买", "点击咨询", "联系电话", "微信",
    "免费试用", "限时优惠", "在线咨询", "加微信"
]

# 低质域名
LOW_QUALITY_DOMAINS = DOMAIN_TRUST_LEVELS["low"]

# 搜索引擎配置
SEARCH_ENGINES = {
    "bing": "https://www.bing.com/search?q={query}",
    "baidu": "https://www.baidu.com/s?wd={query}",
    "duckduckgo": "https://html.duckduckgo.com/html/?q={query}"
}

# CSS选择器配置
SELECTORS = {
    "bing": {
        "container": ["#b_results > li.b_algo", "li.b_algo", "#b_results > li"],
        "title": ["h2 a", "h2 > a", ".b_title h2 a"],
        "link": ["h2 a", "h2 > a", ".b_title h2 a"],
        "snippet": [".b_caption p", ".b_snippet", ".b_desc", ".b_caption"]
    },
    "baidu": {
        "container": ["div.result.c-container", "div.c-container", "div.result-op"],
        "title": ["h3.t a", "h3 a", ".t a"],
        "link": ["h3.t a", "h3 a", ".t a"],
        "snippet": [".c-abstract", ".abstract", ".content-right"]
    },
    "duckduckgo": {
        "container": [".result", ".results_main"],
        "title": [".result__a", "a.result__a"],
        "link": [".result__url", "a.result__url"],
        "snippet": [".result__snippet", ".result__body"]
    }
}

# 质量打分权重
QUALITY_WEIGHTS = {
    "timeliness": 0.25,
    "authority": 0.25,
    "completeness": 0.20,
    "relevance": 0.20,
    "ad_penalty": 0.10
}

# 筛选阈值
FILTER_THRESHOLDS = {
    "min_quality_score": 0.5,
    "max_results": 5,
    "final_results": 3,
    "similarity_threshold": 0.7
}