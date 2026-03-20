# whitelist_config.py
"""
白名单域名配置 - 只抓这些可信、开放的站点
"""

# 白名单域名
CONTENT_WHITELIST = {
    # 新闻门户 - 开放，反爬弱
    "news.qq.com": {
        "name": "腾讯新闻",
        "selectors": ["div.content-article", "div.article-content", "article"],
        "trust_level": "high",
        "category": "news"
    },
    "new.qq.com": {
        "name": "腾讯新闻",
        "selectors": ["div.content-article", "div.article-content", "article"],
        "trust_level": "high",
        "category": "news"
    },
    "sina.com.cn": {
        "name": "新浪",
        "selectors": ["div#artibody", "div.article-content", "article"],
        "trust_level": "high",
        "category": "news"
    },
    "sohu.com": {
        "name": "搜狐",
        "selectors": ["div.article", "div#content", "article"],
        "trust_level": "medium",
        "category": "news"
    },
    "163.com": {
        "name": "网易",
        "selectors": ["div.post_body", "div.article-content", "article"],
        "trust_level": "medium",
        "category": "news"
    },

    # 政府/官方 - 最可信
    "gov.cn": {
        "name": "政府网站",
        "selectors": ["div.content", "div.main", "article", ".TRS_Editor"],
        "trust_level": "high",
        "category": "official"
    },
    "edu.cn": {
        "name": "教育网站",
        "selectors": ["div.content", "div.main", "article"],
        "trust_level": "high",
        "category": "official"
    },

    # 技术/开发者 - 开放
    "csdn.net": {
        "name": "CSDN",
        "selectors": ["div#content_views", "article", "div.blog-content"],
        "trust_level": "medium",
        "category": "tech"
    },
    "cnblogs.com": {
        "name": "博客园",
        "selectors": ["div#post_detail", "div.post", "article"],
        "trust_level": "medium",
        "category": "tech"
    },
    "juejin.cn": {
        "name": "掘金",
        "selectors": ["div.markdown-body", "article"],
        "trust_level": "medium",
        "category": "tech"
    },
    "segmentfault.com": {
        "name": "SegmentFault",
        "selectors": ["div.article-content", "article"],
        "trust_level": "medium",
        "category": "tech"
    },

    # 百科/知识
    "wikipedia.org": {
        "name": "维基百科",
        "selectors": ["div#mw-content-text", "div.mw-parser-output"],
        "trust_level": "high",
        "category": "knowledge"
    },
    "baike.baidu.com": {
        "name": "百度百科",
        "selectors": ["div.lemma-summary", "div.main-content"],
        "trust_level": "medium",
        "category": "knowledge"
    },

    # ===== 硬件/数码站点（新增） =====
    "zol.com.cn": {
        "name": "中关村在线",
        "selectors": ["div.article-content", "div#article-content", "article", "div.content"],
        "trust_level": "medium",
        "category": "hardware"
    },
    "pconline.com.cn": {
        "name": "太平洋电脑网",
        "selectors": ["div.content", "div.article", "article", "div.main-content"],
        "trust_level": "medium",
        "category": "hardware"
    },
    "expreview.com": {
        "name": "超能网",
        "selectors": ["div.article-content", "article", "div.content"],
        "trust_level": "high",
        "category": "hardware"
    },
    "chiphell.com": {
        "name": "Chiphell",
        "selectors": ["div.message", "div.post-content", "article", "div#postlist"],
        "trust_level": "medium",
        "category": "hardware"
    },
    "ithome.com": {
        "name": "IT之家",
        "selectors": ["div#content", "div.article-content", "article", "div.post_content"],
        "trust_level": "medium",
        "category": "hardware"
    },
    "mydrivers.com": {
        "name": "驱动之家",
        "selectors": ["div#content", "div.article", "article"],
        "trust_level": "medium",
        "category": "hardware"
    },
    "fjd.com.cn": {  # 飞桨，可能有硬件评测
        "name": "飞桨",
        "selectors": ["div.content", "article"],
        "trust_level": "medium",
        "category": "tech"
    },
# 汽车垂直媒体（高优先级）
    "dongchedi.com": {
        "name": "懂车帝",
        "selectors": ["div.article-content", "article", "div#content", "div.content"],
        "trust_level": "high",
        "category": "car"
    },
    "autohome.com.cn": {
        "name": "汽车之家",
        "selectors": ["div.article-content", "div#content", "article"],
        "trust_level": "high",
        "category": "car"
    },
    "yoojia.com": {
        "name": "有驾",
        "selectors": ["div.article-content", "article", "div.content"],
        "trust_level": "medium",
        "category": "car"
    },
    "pcauto.com.cn": {
        "name": "太平洋汽车网",
        "selectors": ["div.article", "div.content", "article"],
        "trust_level": "medium",
        "category": "car"
    },
    "xcar.com.cn": {
        "name": "爱卡汽车",
        "selectors": ["div.article-content", "div#content", "article"],
        "trust_level": "medium",
        "category": "car"
    },
    "sina.com.cn": {
        "name": "新浪汽车",  # 已有，但确认包含汽车频道
        "selectors": ["div#artibody", "div.article-content", "article"],
        "trust_level": "high",
        "category": "car"  # 可以覆盖news和car
    },
    # ===== 游戏攻略/社区（新增） =====
    "taptap.cn": {
        "name": "TapTap",
        "selectors": ["div.moment-content", "div.content", "article.moment-article", "div.post-detail-content"],
        "trust_level": "medium",
        "category": "game"
    },
    "taptap.io": {
        "name": "TapTap国际",
        "selectors": ["div.moment-content", "div.content", "article"],
        "trust_level": "medium",
        "category": "game"
    },
    "bilibili.com": {
        "name": "Bilibili",
        "selectors": ["div.article-content", "div#read-article-holder", "article.read-article", "div.content"],
        "trust_level": "medium",
        "category": "game"
    },
    "nga.cn": {
        "name": "NGA玩家社区",
        "selectors": ["div.post-content", "div#postcontent", "article.post"],
        "trust_level": "medium",
        "category": "game"
    },
    "nga.178.com": {
        "name": "NGA论坛",
        "selectors": ["div.post-content", "div#postcontent", "article.post"],
        "trust_level": "medium",
        "category": "game"
    },
    "gamersky.com": {
        "name": "游民星空",
        "selectors": ["div.article-content", "div.content", "article", "div.Mid2L_con"],
        "trust_level": "medium",
        "category": "game"
    },
    "3dmgame.com": {
        "name": "3DM",
        "selectors": ["div.article-content", "div.content", "article", "div.litpic"],
        "trust_level": "medium",
        "category": "game"
    },
    "ign.com.cn": {
        "name": "IGN中国",
        "selectors": ["div.article-content", "article", "div.content"],
        "trust_level": "high",
        "category": "game"
    },
    "vgtime.com": {
        "name": "游戏时光",
        "selectors": ["div.article-content", "article", "div.content"],
        "trust_level": "medium",
        "category": "game"
    },
    "a9vg.com": {
        "name": "A9VG电玩部落",
        "selectors": ["div.post-content", "div.content", "article"],
        "trust_level": "medium",
        "category": "game"
    },
    # ===== 二游官方社区（新增） =====

    # 米哈游系
    "miyoushe.com": {
        "name": "米游社",
        "selectors": ["div.mhy-article-content", "div.post-content", "article.mhy-article", "div.content"],
        "trust_level": "high",
        "category": "game"
    },
    "hoyolab.com": {
        "name": "HoYoLAB",
        "selectors": ["div.article-content", "div.post-body", "article", "div.content"],
        "trust_level": "high",
        "category": "game"
    },

    # 鹰角网络（明日方舟）
    "hypergryph.com": {
        "name": "鹰角网络",
        "selectors": ["div.article-content", "div.content", "article"],
        "trust_level": "high",
        "category": "game"
    },
    "ak.hypergryph.com": {
        "name": "明日方舟官网",
        "selectors": ["div.news-content", "div.article", "article"],
        "trust_level": "high",
        "category": "game"
    },

    # 库洛游戏（鸣潮/战双）
    "kurogame.com": {
        "name": "库洛游戏",
        "selectors": ["div.article-content", "div.content", "article"],
        "trust_level": "high",
        "category": "game"
    },
    "mc.kurogame.com": {
        "name": "鸣潮官网",
        "selectors": ["div.news-detail", "div.content", "article"],
        "trust_level": "high",
        "category": "game"
    },
    "pns.kurogame.com": {
        "name": "战双帕弥什官网",
        "selectors": ["div.news-detail", "div.content", "article"],
        "trust_level": "high",
        "category": "game"
    },

    # 散爆网络（少女前线）
    "sunborngame.com": {
        "name": "散爆网络",
        "selectors": ["div.article-content", "div.content", "article"],
        "trust_level": "medium",
        "category": "game"
    },
    "gf-cn.sunborngame.com": {
        "name": "少女前线",
        "selectors": ["div.news-content", "div.content", "article"],
        "trust_level": "medium",
        "category": "game"
    },

    # 悠星网络（碧蓝档案/雀魂）
    "yostar.cn": {
        "name": "悠星网络",
        "selectors": ["div.article-content", "div.content", "article"],
        "trust_level": "medium",
        "category": "game"
    },
    "bluearchive-cn.com": {
        "name": "碧蓝档案国服",
        "selectors": ["div.news-detail", "div.content", "article"],
        "trust_level": "medium",
        "category": "game"
    },

    # 蛮啾网络（碧蓝航线）
    "manjuu.cn": {
        "name": "蛮啾网络",
        "selectors": ["div.article-content", "div.content", "article"],
        "trust_level": "medium",
        "category": "game"
    },
    "azurlane.bilibili.com": {  # 碧蓝航线B站服
        "name": "碧蓝航线",
        "selectors": ["div.article-content", "div.content", "article"],
        "trust_level": "medium",
        "category": "game"
    },

    # 网易系二游
    "id5.163.com": {  # 第五人格
        "name": "第五人格",
        "selectors": ["div.news-content", "div.content", "article"],
        "trust_level": "medium",
        "category": "game"
    },
    "onmyoji.163.com": {  # 阴阳师
        "name": "阴阳师",
        "selectors": ["div.news-detail", "div.content", "article"],
        "trust_level": "medium",
        "category": "game"
    },

    # 腾讯系二游
    "gp.qq.com": {  # 光与夜之恋
        "name": "光与夜之恋",
        "selectors": ["div.article-content", "div.content", "article"],
        "trust_level": "medium",
        "category": "game"
    },
    "foodgame.qq.com": {  # 食物语
        "name": "食物语",
        "selectors": ["div.news-content", "div.content", "article"],
        "trust_level": "medium",
        "category": "game"
    },

    # 叠纸游戏（恋与制作人/深空/暖暖）
    "papergames.cn": {
        "name": "叠纸游戏",
        "selectors": ["div.article-content", "div.content", "article"],
        "trust_level": "medium",
        "category": "game"
    },
    "loveanddeepspace.papergames.cn": {  # 恋与深空
        "name": "恋与深空",
        "selectors": ["div.news-detail", "div.content", "article"],
        "trust_level": "medium",
        "category": "game"
    },

    # 库洛/英雄游戏（战双/鸣潮国际）
    "punishinggrayraven.com": {
        "name": "战双国际服",
        "selectors": ["div.article-content", "div.content", "article"],
        "trust_level": "medium",
        "category": "game"
    },
    "wutheringwaves.kurogames.com": {
        "name": "鸣潮国际服",
        "selectors": ["div.news-detail", "div.content", "article"],
        "trust_level": "medium",
        "category": "game"
    },

    # 其他
    "arknights.global": {  # 明日方舟国际服
        "name": "明日方舟国际服",
        "selectors": ["div.article-content", "div.content", "article"],
        "trust_level": "medium",
        "category": "game"
    },
    "priconne-redive.jp": {  # 公主连结日服（参考）
        "name": "公主连结",
        "selectors": ["div.article-content", "div.content"],
        "trust_level": "low",
        "category": "game"
    },
}

# 黑名单 - 明确不抓的（反爬强或需要登录）
CONTENT_BLACKLIST = [
    "zhihu.com",  # 反爬强
    "smzdm.com",  # 需要登录
    "weibo.com",  # 需要登录
    "xiaohongshu.com",  # 需要登录
    "douyin.com",  # 需要登录
    "bilibili.com",  # 动态加载复杂
    "taobao.com",  # 商业敏感
    "jd.com",  # 商业敏感
    "tmall.com",  # 商业敏感
    "amazon.cn",  # 商业敏感
    "amazon.com",  # 商业敏感
]


def get_domain_config(url: str) -> dict:
    """
    获取域名的抓取配置

    Returns:
        dict: 配置信息，如果不在白名单返回None
    """
    from urllib.parse import urlparse

    domain = urlparse(url).netloc.lower()

    # 检查黑名单
    for blocked in CONTENT_BLACKLIST:
        if blocked in domain:
            return {"blocked": True, "reason": f"{blocked} 在黑名单中"}

    # 检查白名单（支持子域名匹配）
    for whitelist_domain, config in CONTENT_WHITELIST.items():
        if whitelist_domain in domain:
            return {
                "blocked": False,
                "domain": whitelist_domain,
                **config
            }

    # 不在任何名单中 - 默认不抓（安全策略）
    return {"blocked": True, "reason": "不在白名单中"}


def should_fetch_content(url: str) -> bool:
    """判断是否应该抓取正文"""
    config = get_domain_config(url)
    return not config.get("blocked", True)