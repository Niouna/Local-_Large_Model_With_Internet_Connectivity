# search_engine.py
"""搜索引擎封装模块（Selenium）- 增强版，支持 Bing AI 摘要"""

import sys
import time
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.common.exceptions import NoSuchElementException, TimeoutException

from .config import SEARCH_ENGINES, SELECTORS


class SearchEngine:
    """搜索引擎封装类"""

    def __init__(self, engine: str = "bing", driver_manager=None):
        self.engine = engine
        self.driver_manager = driver_manager
        self.selectors = SELECTORS.get(engine, SELECTORS["bing"])

    def get_search_url(self, query: str) -> str:
        """构建搜索URL"""
        url_template = SEARCH_ENGINES.get(self.engine, SEARCH_ENGINES["bing"])
        from urllib.parse import quote
        return url_template.format(query=quote(query))

    def _find_element_with_fallback(self, container, selector_list: list):
        """尝试多个选择器，返回第一个成功的"""
        for selector in selector_list:
            try:
                elem = container.find_element(By.CSS_SELECTOR, selector)
                if elem and elem.text.strip():
                    return elem
            except:
                continue
        return None

    def fetch_ai_overview(self, driver) -> dict:
        """
        提取 Bing AI 生成的摘要（Copilot/AI Overview）

        Returns:
            dict: {
                "has_ai_overview": bool,
                "title": str,
                "content": str,
                "sources": list,
                "type": str
            }
        """
        try:
            # 等待 AI 摘要区域加载（最多5秒）
            wait = WebDriverWait(driver, 5)

            # AI 摘要主容器选择器（多种可能）
            ai_container_selectors = [
                "div.b_ans.b_top.b_gsrt",  # 顶部AI摘要
                "div.b_ans.b_topborder.b_gsrt",  # 带边框的AI摘要
                "div#b_context div.b_ans",  # 侧边AI摘要
                "div.gs_r.gs_fma",  # 特色摘要
            ]

            ai_container = None
            for selector in ai_container_selectors:
                try:
                    ai_container = wait.until(
                        EC.presence_of_element_located((By.CSS_SELECTOR, selector))
                    )
                    if ai_container and ai_container.text.strip():
                        break
                except:
                    continue

            if not ai_container or not ai_container.text.strip():
                return {"has_ai_overview": False}

            # 提取标题
            title = ""
            title_selectors = [
                "div.gs_caphead_main",
                "div.b_focusText",
                "h2.b_topTitle",
                "div.gs_ti",
            ]
            for sel in title_selectors:
                try:
                    title_elem = ai_container.find_element(By.CSS_SELECTOR, sel)
                    title = title_elem.text.strip()
                    if title:
                        break
                except:
                    continue

            # 提取内容（多段合并）
            content_parts = []
            content_selectors = [
                "div.gs_temp_content",
                "div.gs_text",
                "div.b_focusText",
                "div.gs_r.gs_fma div.gs_ri",  # 特色摘要内容
                "div.b_caption p",
            ]

            for sel in content_selectors:
                try:
                    elems = ai_container.find_elements(By.CSS_SELECTOR, sel)
                    for elem in elems:
                        text = elem.text.strip()
                        if text and len(text) > 20:  # 过滤短文本
                            content_parts.append(text)
                except:
                    continue

            # 去重并合并
            seen = set()
            unique_content = []
            for part in content_parts:
                if part not in seen:
                    seen.add(part)
                    unique_content.append(part)

            content = "\n\n".join(unique_content[:3])  # 最多3段

            # 提取来源
            sources = []
            source_selectors = [
                "div.b_caption cite",
                "div.gs_citation cite",
                "div.gs_r div.gs_ri div.gs_a",
                "div.b_attribution cite",
            ]

            for sel in source_selectors:
                try:
                    source_elems = ai_container.find_elements(By.CSS_SELECTOR, sel)
                    for s in source_elems:
                        src = s.text.strip()
                        if src and src not in sources:
                            sources.append(src)
                except:
                    continue

            # 验证内容质量
            if len(content) < 100:  # 内容太短，可能不是有效AI摘要
                return {"has_ai_overview": False}

            print(f"[AI Overview] 检测到 Bing AI 摘要: {title[:50]}...", file=sys.stderr)

            return {
                "has_ai_overview": True,
                "title": title or "AI生成摘要",
                "content": content,
                "sources": sources[:3],  # 最多3个来源
                "type": "ai_generated",
                "length": len(content)
            }

        except (NoSuchElementException, TimeoutException):
            return {"has_ai_overview": False}
        except Exception as e:
            print(f"[AI Overview] 提取失败: {str(e)[:100]}", file=sys.stderr)
            return {"has_ai_overview": False}

    def fetch_results(self, driver, query: str, max_results: int = 10) -> list:
        """执行搜索并抓取结果（含AI摘要检测）"""
        try:
            url = self.get_search_url(query)
            driver.get(url)
            time.sleep(3)  # 等待加载

            results = []

            # 获取所有结果项
            items = driver.find_elements(By.CSS_SELECTOR, "#b_results > li")

            for item in items[:max_results * 2]:
                # 检测AI摘要特征
                ai_indicators = item.find_elements(By.CSS_SELECTOR,
                                                   "div.qna-mf, div.gs_temp_content, div.gs_caphead_main")

                if ai_indicators:
                    # 是AI摘要
                    title = item.find_element(By.CSS_SELECTOR, "div.gs_caphead_main").text if item.find_elements(
                        By.CSS_SELECTOR, "div.gs_caphead_main") else "Bing AI推荐"
                    content = item.find_element(By.CSS_SELECTOR, "div.gs_text").text if item.find_elements(
                        By.CSS_SELECTOR, "div.gs_text") else item.text
                    results.append({
                        "title": title,
                        "link": "bing://ai-overview",
                        "snippet": content,
                        "is_ai_overview": True
                    })
                else:
                    # 普通结果
                    try:
                        title = item.find_element(By.CSS_SELECTOR, "h2 a").text
                        link = item.find_element(By.CSS_SELECTOR, "h2 a").get_attribute("href")
                        snippet = item.find_element(By.CSS_SELECTOR, ".b_caption p").text if item.find_elements(
                            By.CSS_SELECTOR, ".b_caption p") else ""
                        results.append({"title": title, "link": link, "snippet": snippet, "is_ai_overview": False})
                    except:
                        continue

            return results

        except Exception as e:
            print(f"[Search] 失败: {e}")
            return []