# quality_scorer.py
"""搜索结果质量打分模块"""

import re
import time
from urllib.parse import urlparse
from .config import DOMAIN_TRUST_LEVELS, QUALITY_WEIGHTS


def get_domain_trust_level(link: str) -> str:
    """获取域名信誉等级"""
    # AI 摘要虚拟链接特殊处理
    if link.startswith("bing://"):
        return "high"

    domain = urlparse(link).netloc.lower()

    for level, domains in DOMAIN_TRUST_LEVELS.items():
        if any(d in domain for d in domains):
            return level

    return "medium"  # 默认中等


def extract_year(text: str) -> list:
    """提取文本中的年份"""
    patterns = [r'\b(20\d{2})\b', r'\b(19\d{2})\b']
    years = []
    for pattern in patterns:
        years.extend(re.findall(pattern, text))
    return years


def calculate_quality_score(result: dict, query: str = "") -> float:
    """
    计算搜索结果质量分数 (0-1.5)

    维度：
    - 时效性 (25%): 是否包含当前年份
    - 权威性 (25%): 域名信誉等级
    - 完整性 (20%): 摘要长度、是否包含数据
    - 相关性 (20%): 查询词匹配度
    - 广告惩罚 (10%): 广告特征扣分

    增强：AI 生成摘要获得额外加分（最高1.5）
    """

    # AI 摘要特殊处理
    if result.get("is_ai_overview"):
        score = 0.9  # 基础分很高
        score += QUALITY_WEIGHTS["authority"]  # +0.25，Bing官方生成

        # 内容长度加分
        content_len = len(result.get("snippet", ""))
        if content_len > 500:
            score += 0.1

        return min(1.5, score)  # 最高1.5分，超过普通结果的1.0上限

    # 普通结果打分
    score = 0.5  # 基础分

    title = result.get('title', '').lower()
    link = result.get('link', '')
    snippet = result.get('snippet', '').lower()
    full_text = f"{title} {snippet}"

    # 1. 时效性 (最高 +0.25)
    current_year = time.strftime("%Y")
    years = extract_year(full_text)
    if current_year in years:
        score += QUALITY_WEIGHTS["timeliness"]
    elif any(str(int(current_year) - 1) in years for y in years):
        score += QUALITY_WEIGHTS["timeliness"] * 0.5

    # 2. 权威性 (最高 +0.25)
    trust_level = get_domain_trust_level(link)
    if trust_level == "high":
        score += QUALITY_WEIGHTS["authority"]
    elif trust_level == "medium":
        score += QUALITY_WEIGHTS["authority"] * 0.6

    # 3. 完整性 (最高 +0.20)
    if len(snippet) > 80:
        score += QUALITY_WEIGHTS["completeness"]
    elif len(snippet) > 40:
        score += QUALITY_WEIGHTS["completeness"] * 0.6

    # 检查是否包含数据（数字、百分比等）
    has_data = bool(re.search(r'\d+[\d,.]*(?:%|元|GB|TB|MHz|GHz)?', full_text))
    if has_data:
        score += QUALITY_WEIGHTS["completeness"] * 0.4

    # 4. 相关性 (最高 +0.20)
    if query:
        query_words = query.lower().split()
        if len(query_words) > 0:
            match_count = sum(1 for word in query_words if len(word) > 1 and word in full_text)
            relevance = match_count / len(query_words)
            score += QUALITY_WEIGHTS["relevance"] * relevance

    # 5. 广告惩罚 (最高 -0.10)
    ad_indicators = ['广告', '推广', '赞助', '点击', '购买']
    ad_count = sum(1 for ind in ad_indicators if ind.lower() in full_text)
    if ad_count > 0:
        score -= QUALITY_WEIGHTS["ad_penalty"] * min(ad_count, 3)

    return max(0.0, min(1.0, score))


def score_and_sort(results: list, query: str = "") -> list:
    """对所有结果打分并排序（支持AI摘要超分）"""
    for result in results:
        result['quality_score'] = round(calculate_quality_score(result, query), 3)
        # AI 摘要不需要再算 trust_level，已经在 calculate 里处理
        if not result.get("is_ai_overview"):
            result['trust_level'] = get_domain_trust_level(result.get('link', ''))
        else:
            result['trust_level'] = 'high'

    # 按质量分降序，AI 摘要自然排在前面（1.5分 > 1.0分）
    return sorted(results, key=lambda x: x.get('quality_score', 0), reverse=True)