# filters.py
"""搜索结果过滤模块"""

from urllib.parse import urlparse
from .config import AD_KEYWORDS, LOW_QUALITY_DOMAINS, FILTER_THRESHOLDS


def is_ad(title: str, link: str, snippet: str) -> bool:
    """识别广告结果"""
    text = f"{title} {link} {snippet}".lower()
    return any(keyword.lower() in text for keyword in AD_KEYWORDS)


def is_low_quality_domain(link: str) -> bool:
    """判断是否为低质域名"""
    domain = urlparse(link).netloc.lower()
    return any(low_domain in domain for low_domain in LOW_QUALITY_DOMAINS)


def is_official_source(link: str) -> bool:
    """判断是否为权威来源"""
    domain = urlparse(link).netloc.lower()
    official_keywords = ['gov.cn', 'edu.cn', 'ac.cn', 'org.cn']
    return any(keyword in domain for keyword in official_keywords)


def calculate_similarity(s1: str, s2: str) -> float:
    """计算字符串相似度（Jaccard）"""
    if not s1 or not s2:
        return 0.0

    set1 = set(s1.lower().split())
    set2 = set(s2.lower().split())

    if not set1 or not set2:
        return 0.0

    intersection = len(set1 & set2)
    union = len(set1 | set2)

    return intersection / union if union > 0 else 0.0


def remove_duplicates(results: list, threshold: float = None) -> list:
    """移除相似结果"""
    if threshold is None:
        threshold = FILTER_THRESHOLDS["similarity_threshold"]

    unique = []
    for result in results:
        is_dup = False
        for existing in unique:
            title_sim = calculate_similarity(result.get('title', ''), existing.get('title', ''))
            link_sim = calculate_similarity(result.get('link', ''), existing.get('link', ''))

            if title_sim > threshold or link_sim > threshold:
                is_dup = True
                # 保留质量分数高的
                if result.get('quality_score', 0) > existing.get('quality_score', 0):
                    unique.remove(existing)
                    unique.append(result)
                break

        if not is_dup:
            unique.append(result)

    return unique


def filter_results(results: list, query: str = "") -> list:
    """应用所有过滤规则"""
    filtered = []

    for result in results:
        title = result.get('title', '')
        link = result.get('link', '')
        snippet = result.get('snippet', '')

        # 过滤广告
        if is_ad(title, link, snippet):
            continue

        # 过滤低质域名
        if is_low_quality_domain(link):
            continue

        # 必须有标题和链接
        if not title or not link:
            continue

        filtered.append(result)

    return filtered