# test_v1.py
from test.search_selenium import search_with_selenium
import json


def main():
    # 在这里修改测试用的查询词和结果数量
    query = "2026年高考时间"
    max_results = 3

    print(f"🔍 测试搜索: '{query}' (最多 {max_results} 条结果)")
    result = search_with_selenium(query, max_results)

    print("\n📦 搜索结果 JSON:")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()