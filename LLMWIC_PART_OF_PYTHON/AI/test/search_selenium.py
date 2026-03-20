import sys
import json
from selenium.webdriver.common.by import By
import time
from utils.driver_manager import DriverManager
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

# 可在脚本开头选择搜索引擎
SEARCH_ENGINE = "bing"  # 可选: "bing", "baidu", "duckduckgo"

def get_search_url(query):
    """根据选择的搜索引擎返回对应的搜索URL"""
    if SEARCH_ENGINE == "bing":
        return f"https://www.bing.com/search?q={query}"
    elif SEARCH_ENGINE == "baidu":
        return f"https://www.baidu.com/s?wd={query}"
    elif SEARCH_ENGINE == "duckduckgo":
        return f"https://html.duckduckgo.com/html/?q={query}"
    else:
        raise ValueError("不支持的搜索引擎，请选择 bing / baidu / duckduckgo")

def get_result_selectors(engine):
    """根据搜索引擎返回对应的 CSS 选择器"""
    if engine == "bing":
        return {
            "container": "#b_results > li.b_algo",
            "title": "h2 a",
            "link": "h2 a",
            "snippet": ".b_caption p"
        }
    elif engine == "baidu":
        return {
            "container": "div.result.c-container, div.result-op.c-container",
            "title": "h3.t a",
            "link": "h3.t a",
            "snippet": ".c-abstract"
        }
    elif engine == "duckduckgo":
        return {
            "container": ".result",
            "title": ".result__a",
            "link": ".result__url",
            "snippet": ".result__snippet"
        }
    else:
        return {}

def search_with_selenium(query, max_results=3):
    driver = DriverManager.get_driver()
    wait = WebDriverWait(driver, 10)  # 显式等待，最多10秒

    try:
        # 构建搜索URL
        url = get_search_url(query)
        print(f"正在访问: {url}", file=sys.stderr)
        driver.get(url)

        # 等待搜索结果容器加载
        selectors = get_result_selectors(SEARCH_ENGINE)
        wait.until(EC.presence_of_element_located((By.CSS_SELECTOR, selectors["container"])))

        # 再等一小会儿让页面稳定
        time.sleep(1)

        results = []
        # 定位所有搜索结果容器
        containers = driver.find_elements(By.CSS_SELECTOR, selectors["container"])[:max_results]

        for container in containers:
            try:
                # 标题
                title_elem = container.find_element(By.CSS_SELECTOR, selectors["title"])
                title = title_elem.text.strip()

                # 链接
                if selectors["link"] == selectors["title"]:
                    # 标题就是链接的情况
                    link = title_elem.get_attribute("href")
                else:
                    link_elem = container.find_element(By.CSS_SELECTOR, selectors["link"])
                    link = link_elem.get_attribute("href")

                # 摘要（Bing 和百度可能有不同的类名）
                snippet = ""
                try:
                    snippet_elem = container.find_element(By.CSS_SELECTOR, selectors["snippet"])
                    snippet = snippet_elem.text.strip()
                except:
                    pass  # 有些结果可能没有摘要

                results.append({
                    'title': title,
                    'link': link,
                    'snippet': snippet
                })
            except Exception as e:
                print(f"解析单条结果出错: {e}", file=sys.stderr)
                continue

        return {"results": results}

    finally:
        driver.quit()

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Missing query"}))
        sys.exit(1)

    query = sys.argv[1]
    max_results = int(sys.argv[2]) if len(sys.argv) > 2 else 3

    # 打印一下当前使用的搜索引擎（便于调试）
    print(f"使用搜索引擎: {SEARCH_ENGINE}", file=sys.stderr)

    output = search_with_selenium(query, max_results)
    print(json.dumps(output, ensure_ascii=False))