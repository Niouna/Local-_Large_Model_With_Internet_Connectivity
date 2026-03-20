# test_enhanced.py
"""
测试增强版搜索（白名单正文抓取）
"""

import sys
from pathlib import Path

# 添加父目录到路径
sys.path.insert(0, str(Path(__file__).parent.parent))

from utils.driver_manager import DriverManager
from search_module import search_enhanced


def main():
    query = ("崩铁目前较为无脑的配队")

    print(f"🔍 增强版搜索测试：'{query}'")
    print("=" * 60)
    print("模式：白名单正文抓取")
    print("=" * 60)

    result = search_enhanced(
        query,
        max_results=3,
        fetch_content=True,
        driver_manager=DriverManager
    )

    print(f"\n📦 搜索结果:")
    print(f"查询：{result['query']}")
    print(f"状态：{result['status']}")
    print(f"结果数：{result['result_count']}")
    print(f"耗时：{result['elapsed_seconds']}秒")

    # 增强信息统计
    enhanced_info = result.get("enhanced_info", {})
    print(f"\n📈 增强统计:")
    print(f"  成功抓取正文：{enhanced_info.get('content_fetched_count', 0)}/{enhanced_info.get('total_results', 0)} 个")
    print(f"  抓取时间：{enhanced_info.get('fetch_time', 'N/A')}")

    print(f"\n📝 增强版LLM摘要:")
    print("=" * 60)
    print(result.get('summary_for_llm', '无'))
    print("=" * 60)

    print(f"\n🔗 详细结果:")
    for i, r in enumerate(result['results'], 1):
        print(f"\n  [{i}] {r['title']}")
        print(f"      链接：{r['link']}")
        print(f"      可信度：{r['trust_level']} | 质量分：{r['quality_score']}")

        # 内容抓取状态
        if r.get('content_fetched'):
            print(f"      📄 正文：✅ 已抓取 ({r.get('content_length', 0)} 字符)")
            # 显示增强提取的关键信息
            enhanced_facts = r.get('key_facts_enhanced', {})
            if enhanced_facts.get('products'):
                print(f"      🏷️  产品：{enhanced_facts['products']}")
            if enhanced_facts.get('prices'):
                print(f"      💰 价格：{enhanced_facts['prices']}")
        else:
            reason = r.get('content_skip_reason') or r.get('content_error', '未知')
            print(f"      📄 正文：❌ 未抓取 ({reason})")

        # 原始摘要预览
        snippet = r.get('snippet', '')
        if snippet:
            print(f"      📝 摘要：{snippet[:100]}...")

    # 保存结果
    output_path = Path(__file__).parent / 'enhanced_search_result.json'
    import json
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(f"\n💾 完整结果已保存：{output_path}")


if __name__ == "__main__":
    main()