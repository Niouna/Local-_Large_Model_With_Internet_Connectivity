"""
天气查询统一API
组合所有模块，提供简单易用的接口
"""

from pathlib import Path
from typing import Optional, Dict

from .data_loader import ProvinceDataLoader, City
from .city_matcher import CityMatcher, MatchResult
from .page_parser import WeatherPageParser, ParsedWeather
from .weather_fetcher import WeatherFetcher
from .formatter import WeatherFormatter


class WeatherAPI:
    """天气查询API"""

    def __init__(
            self,
            data_dir: Path,
            driver_manager,
            output_style: str = "default"
    ):
        """
        Args:
            data_dir: province_code目录路径
            driver_manager: DriverManager实例
            output_style: 输出风格 default/simple/detailed/json
        """
        self.data_loader = ProvinceDataLoader(data_dir)
        self.matcher = CityMatcher(self.data_loader)
        self.fetcher = WeatherFetcher(driver_manager)
        self.formatter = WeatherFormatter(output_style)

    def query(self, city_query: str) -> Dict:
        """
        查询城市天气

        Returns:
            {
                "success": bool,
                "city": str,
                "formatted": str,      # 给LLM的格式化文本
                "data": ParsedWeather, # 原始数据（可选）
                "error": str           # 失败时
            }
        """
        # 1. 匹配城市
        match = self.matcher.match(city_query)
        if not match:
            # 尝试搜索建议
            suggestions = self.matcher.search(city_query, limit=3)
            return {
                "success": False,
                "error": f"未找到城市: {city_query}",
                "suggestions": [
                    f"{s.city.name}（{s.city.province_name}）"
                    for s in suggestions
                ]
            }

        city = match.city

        # 2. 构建URL
        url = f"https://www.nmc.cn{city.url}"

        # 3. 抓取页面
        try:
            html = self.fetcher.fetch(url, wait_for_data=True)
        except Exception as e:
            return {
                "success": False,
                "city": city.name,
                "error": f"抓取失败: {str(e)}"
            }

        # 4. 解析
        try:
            parser = WeatherPageParser(html)
            data = parser.parse()
        except Exception as e:
            return {
                "success": False,
                "city": city.name,
                "error": f"解析失败: {str(e)}"
            }

        # 5. 格式化
        formatted = self.formatter.format(data)

        return {
            "success": True,
            "city": city.name,
            "province": city.province_name,
            "match_type": match.match_type,
            "formatted": formatted,
            "data": data,
            "source_url": url
        }

    def quick_query(self, city_query: str) -> str:
        """
        快速查询，直接返回格式化文本

        Returns:
            格式化天气文本，或错误信息
        """
        result = self.query(city_query)
        if result["success"]:
            return result["formatted"]
        else:
            error_msg = result["error"]
            if "suggestions" in result:
                error_msg += f"\n您是否想找：{', '.join(result['suggestions'])}"
            return error_msg

    def set_style(self, style: str):
        """切换输出风格"""
        self.formatter.style = style


# 便捷函数
def create_weather_api(
        data_dir: str,
        driver_manager,
        style: str = "default"
) -> WeatherAPI:
    """创建天气API实例"""
    return WeatherAPI(
        data_dir=Path(data_dir),
        driver_manager=driver_manager,
        output_style=style
    )