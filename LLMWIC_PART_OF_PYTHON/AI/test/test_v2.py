# test.py
"""搜索模块测试脚本"""

import json
import sys
from pathlib import Path

# 添加父目录到路径（确保能导入 search_module）
sys.path.insert(0, str(Path(__file__).parent.parent))

from utils.driver_manager import DriverManager
from search_module import search  # ✅ 正确导入搜索模块


def main():
    query = "目前性价比最高的 ios 移动设备"

    print(f"🔍 测试搜索：'{query}'")
    print("-" * 60)

    result = search(query, max_results=3, driver_manager=DriverManager)

    print(f"\n📦 搜索结果:")
    print(f"查询：{result['query']}")
    print(f"状态：{result['status']}")
    print(f"结果数：{result['result_count']}")
    print(f"耗时：{result['elapsed_seconds']}秒")

    print(f"\n📝 LLM 摘要 (传给 AI 的核心内容):")
    print("=" * 60)
    print(result['summary_for_llm'])
    print("=" * 60)

    print(f"\n🔗 详细结果 (完整传给 AI 的数据):")
    for i, r in enumerate(result['results'], 1):
        print(f"\n  [{i}] {r['title']}")
        print(f"      链接：{r['link']}")
        print(f"      可信度：{r['trust_level']} | 质量分：{r['quality_score']}")

        # 展示完整 snippet
        snippet = r.get('snippet', '')
        print(f"      摘要内容：{snippet[:200]}..." if len(snippet) > 200 else f"      摘要内容：{snippet}")

        # 信息类型
        info_type = r.get('info_type', {})
        print(f"      信息类型：事实={info_type.get('is_fact')}, "
              f"观点={info_type.get('is_opinion')}, "
              f"建议={info_type.get('is_recommendation')}")

        # 信息片段
        fragments = r.get('info_fragments', [])
        if fragments:
            print(f"      提取片段:")
            for f in fragments:
                print(f"        - [{f['type']}] {f['content']}")
        else:
            print(f"      提取片段：(无)")

    # 交叉验证
    print(f"\n🔀 交叉验证:")
    cv = result.get('cross_validation', {})
    print(f"  一致信息：{cv.get('consistent_facts', [])}")
    print(f"  冲突信息：{cv.get('conflicting_facts', [])}")
    print(f"  置信度：{cv.get('confidence', 'unknown')}")

    # 给 AI 的备注
    print(f"\n📌 给 AI 的备注:")
    print(f"  {result.get('note_for_llm', '无')}")

    # 保存完整 JSON 供调试
    output_path = Path(__file__).parent / 'search_result_debug.json'
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(f"\n💾 完整 JSON 已保存到：{output_path}")


if __name__ == "__main__":
    main()  # ✅ 必须调用 main()