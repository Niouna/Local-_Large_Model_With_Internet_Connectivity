# search_api.py
"""搜索模块统一 API 入口 - 增强版，支持 AI 摘要"""

import sys
import json
import time
from datetime import datetime
from collections import Counter

from .config import FILTER_THRESHOLDS
from .filters import filter_results, remove_duplicates
from .quality_scorer import score_and_sort, get_domain_trust_level
from .info_extractor import extract_info_fragments, extract_key_facts
from .info_classifier import classify_info_type
from .validator import cross_validate
from .search_engine import SearchEngine


class SearchAPI:
    """
    搜索 API 统一入口

    用法:
        api = SearchAPI(engine="bing")
        result = api.search("显卡性能天梯前 3", max_results=3)
    """

    def __init__(self, engine: str = "bing", driver_manager=None):
        self.engine = engine
        self.driver_manager = driver_manager
        self.search_engine = SearchEngine(engine, driver_manager)

    def search(self, query: str, max_results: int = 3) -> dict:
        """
        执行搜索并返回结构化结果

        Args:
            query: 搜索查询词
            max_results: 返回结果数量

        Returns:
            结构化搜索结果字典
        """
        start_time = time.time()

        # 1. 执行原始搜索（包含 AI 摘要检测）
        driver = self.driver_manager.get_driver() if self.driver_manager else None
        if not driver:
            return {"error": "Driver not available", "query": query, "status": "error"}

        try:
            raw_results = self.search_engine.fetch_results(driver, query, max_results * 3)
        finally:
            driver.quit()

        if not raw_results:
            return {
                "query": query,
                "search_engine": self.engine,
                "search_time": datetime.now().isoformat(),
                "elapsed_seconds": round(time.time() - start_time, 2),
                "result_count": 0,
                "results": [],
                "has_ai_overview": False,
                "cross_validation": {"consistent_facts": [], "conflicting_facts": [], "confidence": "low"},
                "summary_for_llm": "未找到相关搜索结果",
                "note_for_llm": "搜索未返回任何结果，请尝试更换查询词或搜索引擎",
                "status": "no_results"
            }

        # 2. 分离 AI 摘要和普通结果
        ai_results = [r for r in raw_results if r.get("is_ai_overview")]
        normal_results = [r for r in raw_results if not r.get("is_ai_overview")]

        # 3. 过滤普通结果（广告、低质域名）
        filtered_results = filter_results(normal_results, query)

        # 4. 质量打分并排序（包含 AI 摘要的特殊打分）
        scored_results = score_and_sort(filtered_results + ai_results, query)

        # 5. 筛选高质量结果
        min_score = FILTER_THRESHOLDS.get("min_quality_score", 0.5)
        high_quality = [r for r in scored_results if r.get('quality_score', 0) >= min_score]

        # 6. 去重
        unique_results = remove_duplicates(high_quality)

        # 7. 截取最终结果
        final_results = unique_results[:max_results]

        # 8. 添加域名信誉（AI 摘要特殊处理）
        for result in final_results:
            if result.get("is_ai_overview"):
                result['trust_level'] = 'high'
                result['domain'] = 'bing-ai'
            else:
                result['trust_level'] = get_domain_trust_level(result.get("link", ""))

        # 9. 提取关键信息 + 类型分类（跳过 AI 摘要）
        for result in final_results:
            if result.get("is_ai_overview"):
                # AI 摘要已经内容完整，简单分类即可
                result['key_facts'] = {
                    "products": [],
                    "prices": [],
                    "dates": [],
                    "data": [],
                    "recommendations": [result.get("snippet", "")[:200]]
                }
                result['info_fragments'] = []
                result['info_type'] = {
                    "is_fact": True,
                    "is_opinion": False,
                    "is_recommendation": True,
                    "has_data": True,
                    "uncertainty_level": "low"
                }
                continue

            snippet = result.get('snippet', '')
            title = result.get('title', '')
            text = f"{title} {snippet}"

            result['key_facts'] = extract_key_facts(snippet, title)
            result['info_fragments'] = extract_info_fragments(text)
            result['info_type'] = classify_info_type(text)

        # 10. 交叉验证
        validation = cross_validate([r for r in final_results if not r.get("is_ai_overview")])

        # 11. 生成 LLM 友好的摘要
        summary = self._generate_summary(final_results, validation, query)

        # 12. 生成备注
        note = self._generate_note(final_results, query)

        elapsed = time.time() - start_time

        return {
            "query": query,
            "search_engine": self.engine,
            "search_time": datetime.now().isoformat(),
            "elapsed_seconds": round(elapsed, 2),
            "result_count": len(final_results),
            "has_ai_overview": len(ai_results) > 0,
            "ai_overview_count": len(ai_results),
            "results": final_results,
            "cross_validation": validation,
            "summary_for_llm": summary,
            "note_for_llm": note,
            "status": "success" if final_results else "no_results"
        }

    def _generate_summary(self, results: list, validation: dict, query: str) -> str:
        """生成 LLM 友好的摘要"""
        if not results:
            return "未找到相关搜索结果"

        lines = []

        # 分离 AI 摘要和普通结果
        ai_results = [r for r in results if r.get("is_ai_overview")]
        normal_results = [r for r in results if not r.get("is_ai_overview")]

        # ===== AI 摘要优先展示 =====
        if ai_results:
            ai = ai_results[0]
            lines.append("【🤖 Bing AI 智能摘要】")
            lines.append(f"标题：{ai.get('title', 'AI生成答案')}")
            lines.append("")

            # 内容预览
            content = ai.get('snippet', '')
            lines.append(content[:600])
            if len(content) > 600:
                lines.append("...")
            lines.append("")

            # AI 摘要的来源
            ai_sources = ai.get('ai_sources', [])
            if ai_sources:
                lines.append(f"参考来源：{'、'.join(ai_sources[:3])}")
                lines.append("")

            lines.append("—" * 50)
            lines.append("")

        # 收集普通结果的关键信息
        all_products = []
        all_prices = []
        all_recommendations = []

        for r in normal_results:
            key_facts = r.get('key_facts', {})
            all_products.extend(key_facts.get('products', []))
            all_prices.extend(key_facts.get('prices', []))
            all_recommendations.extend(key_facts.get('recommendations', []))

        # 核心信息
        if all_products:
            top_product = Counter(all_products).most_common(1)[0][0]
            lines.append(f"【核心产品】{top_product}")

        if all_prices:
            lines.append(f"【价格参考】{all_prices[0]}")

        if all_recommendations:
            rec_text = all_recommendations[0][:60]
            lines.append(f"【推荐观点】{rec_text}...")

        if (all_products or all_prices or all_recommendations) and normal_results:
            lines.append("")

        # 来源信息
        total_count = len(results)
        lines.append(f"【搜索来源】共{total_count}个（含AI摘要）" if ai_results else f"【搜索来源】共{total_count}个")

        for i, r in enumerate(results, 1):
            title = r.get('title', '')[:40]
            trust = r.get('trust_level', '?')
            score = r.get('quality_score', 0)

            if r.get("is_ai_overview"):
                lines.append(f"  {i}. [🤖AI|{trust}] {title} (质量分：{score:.2f})")
            else:
                lines.append(f"  {i}. [{trust}] {title} (质量分：{score:.2f})")

        # 一致性（仅普通结果）
        if validation.get('consistent_facts') and normal_results:
            lines.append("\n【一致信息】")
            for fact in validation['consistent_facts'][:3]:
                lines.append(f"  • {fact}")

        return "\n".join(lines)

    def _generate_note(self, results: list, query: str) -> str:
        """生成给 AI 的备注"""
        notes = []

        # 时间提醒
        search_time = datetime.now().strftime("%Y年%m月")
        notes.append(f"搜索时间：{search_time}")

        # 来源数量（区分 AI 和普通）
        ai_count = sum(1 for r in results if r.get("is_ai_overview"))
        normal_count = len(results) - ai_count

        if ai_count > 0:
            notes.append(f"信息来源：{normal_count}个网页 + {ai_count}个AI摘要")
        else:
            notes.append(f"信息来源：{normal_count}个网页")

        # AI 摘要提醒
        if ai_count > 0:
            notes.append("包含Bing AI生成的智能摘要，内容已整合多源信息")

        # 信息类型提醒
        normal_results = [r for r in results if not r.get("is_ai_overview")]
        has_opinion = any(r.get('info_type', {}).get('is_opinion', False) for r in normal_results)
        if has_opinion:
            notes.append("注意：部分内容为作者观点，请综合判断")

        # 置信度
        validation = cross_validate(normal_results)
        notes.append(f"信息置信度：{validation.get('confidence', 'unknown')}")

        return " | ".join(notes)


# 便捷函数
def search(query: str, max_results: int = 3, engine: str = "bing", driver_manager=None) -> dict:
    """
    便捷搜索函数

    用法:
        from search_module import search
        result = search("显卡性能天梯前 3")
    """
    api = SearchAPI(engine=engine, driver_manager=driver_manager)
    return api.search(query, max_results)