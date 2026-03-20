"""
增强版搜索API - 带白名单正文抓取
支持硬件、汽车、通用三种提取器
新增：天气查询自动响应
"""
import re
import time
from urllib.parse import urlparse
from datetime import datetime
from typing import Optional

from .game_extractor import is_game_query
# 导入原有模块
from .search_api import SearchAPI
from .whitelist_config import get_domain_config, should_fetch_content
from .info_extractor import extract_info_fragments, extract_key_facts
from .hardware_extractor import extract_hardware_info, merge_with_original as hw_merge
from .car_extractor import extract_car_info, merge_car_with_original, is_car_query

# 新增天气模块
from weather_module import create_weather_api, WeatherAPI


class EnhancedSearchAPI(SearchAPI):
    """
    增强版搜索API
    新增：白名单正文抓取、多类型专用提取器、失败自动回退、天气查询
    """

    def __init__(self, *args, weather_data_dir: str = None, **kwargs):
        """
        初始化，同时初始化天气API

        Args:
            weather_data_dir: 天气数据目录路径，默认使用固定路径
        """
        super().__init__(*args, **kwargs)

        # 路径可配置，默认使用固定路径
        if weather_data_dir is None:
            weather_data_dir = r"D:\个人项目\Local _Large_Model_With_Internet_Connectivity\LLMWIC_PART_OF_PYTHON\AI\province_code"

        self.weather_api = create_weather_api(weather_data_dir, self.driver_manager)

    def search(self, query: str, max_results: int = 3,
               fetch_content: bool = True,
               content_max_length: int = 3000) -> dict:
        """
        执行搜索，可选抓取正文
        如果检测为天气查询，则直接调用天气API返回结果
        """
        # 1. 检测是否为天气查询（优先级最高）
        if self._is_weather_query(query):
            return self._handle_weather(query)

        # 2. 原有搜索逻辑
        result = super().search(query, max_results)

        if not fetch_content:
            return result

        # 3. 对白名单内的结果抓取正文
        enhanced_count = 0

        # 预判断查询类型（只判断一次）
        query_lower = query.lower()
        is_hardware = self._is_hardware_query(query_lower)
        is_car = is_car_query(query)  # 使用car_extractor里的函数
        is_game = is_game_query(query)

        if is_hardware:
            print(f"[Type] 识别为硬件类查询: {query[:30]}...")
        elif is_car:
            print(f"[Type] 识别为游戏类查询: {query[:30]}...")
        elif is_game:
            print(f"[Type] 识别为汽车类查询: {query[:30]}...")


        for item in result.get("results", []):
            link = item.get("link", "")

            # 检查是否在白名单
            config = get_domain_config(link)
            if config.get("blocked"):
                item["content_fetched"] = False
                item["content_skip_reason"] = config.get("reason", "未知")
                continue

            # 尝试抓取正文
            print(f"[Content] 抓取正文: {config.get('name', '未知站点')} - {link[:50]}...")
            content = self._fetch_page_content(link, config)

            if content:
                # 限制长度
                content = content[:content_max_length]
                item["full_content"] = content
                item["content_fetched"] = True
                item["content_length"] = len(content)

                # 选择提取器
                original_facts = item.get("key_facts", {})

                if is_hardware:
                    # 硬件专用提取器
                    hw_info = extract_hardware_info(content, link)
                    merged_facts = hw_merge(original_facts, hw_info)
                    item["info_fragments_enhanced"] = self._hw_to_fragments(hw_info)
                    item["extractor_type"] = "hardware"

                elif is_car:
                    # 汽车专用提取器
                    car_info = extract_car_info(content, link)
                    merged_facts = merge_car_with_original(original_facts, car_info)
                    item["info_fragments_enhanced"] = self._car_to_fragments(car_info)
                    item["extractor_type"] = "car"

                elif is_game:
                    # 游戏提取器
                    from .game_extractor import extract_game_info, merge_game_with_original
                    game_info = extract_game_info(content, link, query)
                    merged_facts = merge_game_with_original(original_facts, game_info)
                    item["info_fragments_enhanced"] = self._game_to_fragments(game_info)
                    item["extractor_type"] = "game"

                else:
                    # 通用提取器
                    enhanced_fragments = extract_info_fragments(content)
                    enhanced_facts = extract_key_facts(content, item.get("title", ""))
                    merged_facts = self._merge_facts(original_facts, enhanced_facts)
                    item["info_fragments_enhanced"] = enhanced_fragments
                    item["extractor_type"] = "general"

                item["key_facts_enhanced"] = merged_facts
                enhanced_count += 1

            else:
                item["content_fetched"] = False
                item["content_error"] = "抓取失败或内容为空"

        # 4. 更新统计信息
        result["enhanced_info"] = {
            "content_fetched_count": enhanced_count,
            "total_results": len(result.get("results", [])),
            "fetch_time": datetime.now().isoformat(),
            "query_type": "hardware" if is_hardware else ("car" if is_car else "general")
        }

        # 5. 如果有增强内容，重新生成摘要
        if enhanced_count > 0:
            result["summary_for_llm"] = self._generate_enhanced_summary(
                result["results"], query, is_hardware, is_car, is_game
            )

        return result

    # ==================== 天气查询专用方法 ====================

    def _is_weather_query(self, query: str) -> bool:
        """
        检测是否为天气查询
        避免"今天天气不错"这种闲聊被误判
        """
        # 必须包含天气关键词
        weather_keywords = ["天气", "气温", "温度", "预报", "下雨", "雪", "雾霾"]
        has_keyword = any(kw in query for kw in weather_keywords)

        if not has_keyword:
            return False

        # 排除明显的非查询句式（闲聊、陈述句）
        exclude_patterns = [
            r'天气不错', r'天气很好', r'天气真', r'天气太',  # 闲聊
            r'像.*天气', r'这种天气', r'那样天气',  # 比喻
            r'天气.*一样', r'天气.*似的',  # 比喻
        ]
        for pattern in exclude_patterns:
            if re.search(pattern, query):
                return False

        # 必须包含地点或询问词
        query_patterns = [
            r'.+天气',  # XX天气
            r'天气.+',  # 天气怎么样
            r'查.*天气',  # 查天气
            r'.*预报',  # XX预报
        ]
        return any(re.search(p, query) for p in query_patterns)

    def _extract_city_from_query(self, query: str) -> Optional[str]:
        """
        从查询中提取城市名
        提高天气API匹配精度
        """
        # 模式：XX天气、XX气温、XX预报
        patterns = [
            r'(.+?)(?:的)?天气',
            r'(.+?)(?:的)?气温',
            r'(.+?)(?:的)?温度',
            r'查询(.+?)天气',
            r'(.+?)天气预报',
            r'(.+?)天气查询',
        ]

        noise_words = ['今天', '明天', '后天', '未来', '现在', '当前', '今天', '明日', '后日']

        for pattern in patterns:
            match = re.search(pattern, query)
            if match:
                city = match.group(1).strip()
                # 过滤掉常见非城市词
                if city and city not in noise_words and len(city) >= 2:
                    return city

        return None

    def _handle_weather(self, query: str) -> dict:
        """
        处理天气查询，返回与 search 方法兼容的结构
        构建丰富的天气摘要
        """
        # 尝试提取城市名（提高匹配精度）
        import time
        start_time = time.time()  # 添加计时

        # 尝试提取城市名（提高匹配精度）
        city = self._extract_city_from_query(query)

        # 如果提取到城市名，直接用；否则让weather_api自己匹配
        if city:
            print(f"[Weather] 提取到城市名: {city}")
            result = self.weather_api.query(city)
        else:
            print(f"[Weather] 使用原始查询: {query}")
            result = self.weather_api.query(query)

        elapsed = round(time.time() - start_time, 2)  # 计算耗时

        if result["success"]:
            # 构建更丰富的摘要（原有代码不变）
            summary_lines = [
                f"【{result['city']}天气预报】",
                f"来源：中央气象台（NMC）",
                f"更新时间：{result['data'].update_time if result.get('data') else '未知'}",
                ""
            ]

            # 添加当前实况（精简版）
            weather_data = result.get("data")
            if weather_data and weather_data.current:
                cur = weather_data.current
                current_parts = []
                if cur.temp:
                    current_parts.append(f"温度{cur.temp}")
                if cur.weather:
                    current_parts.append(f"{cur.weather}")
                if cur.humidity:
                    current_parts.append(f"湿度{cur.humidity}")
                if cur.wind_direction and cur.wind_level:
                    current_parts.append(f"{cur.wind_direction}{cur.wind_level}")

                if current_parts:
                    summary_lines.append(f"当前：{'，'.join(current_parts)}")

            # 添加明天预报
            if weather_data and weather_data.forecast_7d and len(weather_data.forecast_7d) > 1:
                tomorrow = weather_data.forecast_7d[1]
                tomorrow_parts = [
                    f"明天：{tomorrow.temp_low}~{tomorrow.temp_high}" if tomorrow.temp_low else f"明天：{tomorrow.temp_high}",
                    f"{tomorrow.day_weather}" if tomorrow.day_weather else "",
                    f"{tomorrow.day_wind_dir}{tomorrow.day_wind_level}" if tomorrow.day_wind_dir else ""
                ]
                summary_lines.append("，".join([p for p in tomorrow_parts if p]))

            summary_lines.append("")  # 空行分隔

            # 添加完整格式化内容
            full_summary = "\n".join(summary_lines) + result["formatted"]

            return {
                "query": query,
                "status": "success",
                "result_count": 1,
                "query_type": "weather",
                "summary_for_llm": full_summary,
                #"weather_data": result,
                "elapsed_seconds": elapsed,  # 添加耗时
                "search_time": datetime.now().isoformat(),  # 添加时间戳
                "results": [{
                    "title": f"{result['city']}天气预报",
                    "link": result["source_url"],
                    "snippet": result["formatted"][:200],
                    "trust_level": "high",
                    "quality_score": 1.0,
                    "is_weather": True
                }]
            }
        else:
            # 失败返回也添加耗时
            return {
                "query": query,
                "status": "error",
                "error": result["error"],
                "suggestions": result.get("suggestions", []),
                "elapsed_seconds": elapsed,  # 添加耗时
                "search_time": datetime.now().isoformat(),  # 添加时间戳
                "results": []
            }

    # ==================== 原有方法（保持不变） ====================

    def _is_hardware_query(self, query_lower: str) -> bool:
        """判断是否为硬件类查询"""
        hardware_keywords = [
            "cpu", "显卡", "gpu", "主板", "内存", "硬盘", "ssd",
            "intel", "amd", "nvidia", "ryzen", "rtx", "gtx", "core",
            "处理器", "芯片", "纳米", "制程", "架构", "频率", "功耗"
        ]
        return any(kw in query_lower for kw in hardware_keywords)

    def _hw_to_fragments(self, hw_info: dict) -> list:
        """将硬件信息转换为标准片段格式"""
        fragments = []
        for product in hw_info.get("products", []):
            fragments.append({"type": "product", "content": product})
        for price in hw_info.get("prices", []):
            fragments.append({"type": "price", "content": price})
        for spec in hw_info.get("specs", []):
            fragments.append({"type": "data", "content": spec})
        for rec in hw_info.get("recommendations", []):
            fragments.append({"type": "recommendation", "content": rec})
        return fragments

    def _car_to_fragments(self, car_info: dict) -> list:
        """将汽车信息转换为标准片段格式"""
        fragments = []

        # 品牌
        for brand in car_info.get("brands", []):
            fragments.append({"type": "brand", "content": brand})

        # 车型
        for product in car_info.get("products", []):
            fragments.append({"type": "product", "content": product})

        # 价格
        for price in car_info.get("prices", []):
            fragments.append({"type": "price", "content": price})

        # 规格（续航、电池等）
        for spec in car_info.get("specs", []):
            fragments.append({"type": "spec", "content": spec})

        # 评分
        for score in car_info.get("scores", []):
            fragments.append({"type": "score", "content": score})

        return fragments

    def _game_to_fragments(self, game_info: dict) -> list:
        """将游戏信息转换为标准片段格式"""
        fragments = []

        for char in game_info.get("characters", []):
            fragments.append({"type": "character", "content": char})

        for team in game_info.get("teams", []):
            fragments.append({"type": "team", "content": team})

        for weapon in game_info.get("weapons", []):
            fragments.append({"type": "equipment", "content": weapon})

        for rating in game_info.get("ratings", []):
            fragments.append({"type": "rating", "content": rating})

        for stage in game_info.get("stages", []):
            fragments.append({"type": "stage", "content": stage})

        for strategy in game_info.get("strategies", []):
            fragments.append({"type": "strategy", "content": strategy})

        return fragments

    def _fetch_page_content(self, url: str, config: dict) -> str:
        """
        抓取页面正文（白名单专用）
        """
        driver = None
        try:
            driver = self.driver_manager.get_driver()
            driver.get(url)

            # 等待加载
            time.sleep(3)

            # 尝试滚动
            driver.execute_script("window.scrollTo(0, document.body.scrollHeight / 3);")
            time.sleep(1)

            # 按配置的选择器尝试
            selectors = config.get("selectors", ["article", "div.content"])

            from selenium.webdriver.common.by import By

            for selector in selectors:
                try:
                    elem = driver.find_element(By.CSS_SELECTOR, selector)
                    text = elem.text.strip()
                    if len(text) > 200:
                        return text
                except:
                    continue

            # 兜底：拿body全部文本
            try:
                body = driver.find_element(By.TAG_NAME, "body")
                text = body.text.strip()
                if len(text) > 500:
                    return text[:5000]
            except:
                pass

            return ""

        except Exception as e:
            print(f"[Content] 抓取失败: {str(e)[:100]}")
            return ""
        finally:
            if driver:
                driver.quit()

    def _merge_facts(self, original: dict, enhanced: dict) -> dict:
        """合并原始和增强的关键事实（通用）"""
        merged = {}
        for key in ["products", "prices", "dates", "data", "recommendations"]:
            orig_set = set(original.get(key, []))
            enhanced_set = set(enhanced.get(key, []))
            merged[key] = list(orig_set | enhanced_set)[:10]
        return merged

    def _generate_enhanced_summary(self, results: list, query: str,
                                   is_hardware: bool = False,
                                   is_car: bool = False,
                                   is_game: bool = False) -> str:
        """基于增强内容生成更好的摘要"""
        lines = []

        # 分离 AI 摘要和普通结果
        ai_results = [r for r in results if r.get("is_ai_overview")]
        normal_results = [r for r in results if not r.get("is_ai_overview")]

        # ===== AI 摘要优先展示 =====
        if ai_results:
            ai = ai_results[0]  # 通常只有一个
            lines.append("【🤖 Bing AI 摘要】")
            lines.append(f"标题：{ai.get('title', 'AI生成答案')}")
            lines.append("")

            # 内容预览（前500字）
            content = ai.get("snippet", "")
            lines.append(content[:500])
            if len(content) > 500:
                lines.append("...")
            lines.append("")

            # AI 摘要的来源
            ai_sources = ai.get("ai_sources", [])
            if ai_sources:
                lines.append(f"参考来源：{'、'.join(ai_sources)}")
                lines.append("")

            lines.append("—" * 40)
            lines.append("")

        # 收集所有增强信息
        all_products = []
        all_prices = []
        all_specs = []
        all_brands = []
        all_scores = []
        enhanced_sources = []

        for r in results:
            if r.get("content_fetched"):
                enhanced_sources.append(r.get("title", "")[:25])
                facts = r.get("key_facts_enhanced", r.get("key_facts", {}))

                all_products.extend(facts.get("products", []))
                all_prices.extend(facts.get("prices", []))
                all_specs.extend(facts.get("specs", facts.get("data", [])))
                all_brands.extend(facts.get("brands", []))
                all_scores.extend(facts.get("scores", []))

        if not enhanced_sources:
            return "未获取到增强内容"

        # 构建摘要
        lines.append(f"【增强来源】{', '.join(enhanced_sources)}")
        lines.append("")

        if is_game:
            # 游戏类型特殊处理
            game_type = "未知"
            if all_products:
                # 尝试从第一个结果推断
                first_result = results[0] if results else {}
                enhanced_facts = first_result.get("key_facts_enhanced", {})
                game_type = enhanced_facts.get("game_type", "未知游戏")

            lines.append(f"【游戏类型】{game_type}")

            # 角色（合并 products 和 brands）
            all_characters = list(set([p for p in all_products if p] + [b for b in all_brands if b]))
            if all_characters:
                chars_str = "、".join(all_characters[:10])
                lines.append(f"【涉及角色】{chars_str}")

            # 配队/装备/策略
            if all_specs:
                specs_str = " | ".join(all_specs[:6])
                lines.append(f"【配队/装备/关卡】{specs_str}")

            # 强度评级
            if all_scores:
                ratings_str = "、".join(list(set(all_scores))[:5])
                lines.append(f"【强度评级】{ratings_str}")

        elif is_car:
            # 汽车（原有逻辑）
            if all_brands:
                brands_str = "、".join(list(set(all_brands))[:6])
                lines.append(f"【涉及品牌】{brands_str}")

            if all_products:
                from collections import Counter
                top_products = Counter(all_products).most_common(8)
                products_str = "、".join([p[0] for p in top_products])
                lines.append(f"【车型】{products_str}")

            if all_prices:
                price_clean = []
                seen = set()
                for p in all_prices:
                    key = re.findall(r'\d+\.?\d*', p)[0] if re.findall(r'\d+\.?\d*', p) else p
                    if key not in seen:
                        seen.add(key)
                        price_clean.append(p)
                lines.append(f"【价格参考】{'/'.join(price_clean[:5])}")

            if all_specs:
                specs_str = " | ".join(all_specs[:5])
                lines.append(f"【关键参数】{specs_str}")

            if all_scores:
                lines.append(f"【口碑评分】{'、'.join(all_scores[:2])}")

        elif is_hardware:
            # 硬件（原有逻辑）
            if all_products:
                from collections import Counter
                top_products = Counter(all_products).most_common(8)
                products_str = "、".join([p[0] for p in top_products])
                lines.append(f"【产品型号】{products_str}")

            if all_prices:
                price_clean = []
                seen = set()
                for p in all_prices:
                    key = re.findall(r'\d+\.?\d*', p)[0] if re.findall(r'\d+\.?\d*', p) else p
                    if key not in seen:
                        seen.add(key)
                        price_clean.append(p)
                lines.append(f"【价格参考】{'/'.join(price_clean[:5])}")

            if all_specs:
                specs_str = " | ".join(all_specs[:5])
                lines.append(f"【关键参数】{specs_str}")

        else:
            # 通用
            if all_products:
                from collections import Counter
                top_products = Counter(all_products).most_common(8)
                products_str = "、".join([p[0] for p in top_products])
                lines.append(f"【产品/内容】{products_str}")

        # 详细来源（通用）
        lines.append("")
        lines.append("【详细来源】")
        for i, r in enumerate(results, 1):
            status = "✓正文" if r.get("content_fetched") else "摘要"
            trust = r.get("trust_level", "?")
            title = r.get("title", "")[:30]
            ext_type = r.get("extractor_type", "")
            ext_mark = f"({ext_type})" if ext_type else ""
            lines.append(f"  {i}. [{status}|{trust}]{ext_mark} {title}")

        return "\n".join(lines)


# 便捷函数
def search_enhanced(query: str, max_results: int = 3,
                    fetch_content: bool = True,
                    driver_manager=None,
                    weather_data_dir: str = None) -> dict:
    """
    增强搜索便捷函数

    用法:
        from search_module import search_enhanced
        result = search_enhanced("武汉天气", fetch_content=True)
        result = search_enhanced("比亚迪 纯电 推荐", fetch_content=True)
    """
    api = EnhancedSearchAPI(
        engine="bing",
        driver_manager=driver_manager,
        weather_data_dir=weather_data_dir
    )
    return api.search(query, max_results, fetch_content)