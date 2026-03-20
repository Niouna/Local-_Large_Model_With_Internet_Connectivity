"""
天气数据格式化模块
将解析结果转换为LLM友好的文本/JSON
"""

from typing import List, Dict

from .page_parser import ParsedWeather, CurrentWeather, DayForecast, HourForecast


class WeatherFormatter:
    """天气格式化器"""

    def __init__(self, style: str = "default"):
        """
        Args:
            style: 输出风格 - default/detailed/simple/json
        """
        self.style = style

    def format(self, data: ParsedWeather) -> str:
        """格式化输出"""
        if self.style == "json":
            return self._format_json(data)
        elif self.style == "simple":
            return self._format_simple(data)
        elif self.style == "detailed":
            return self._format_detailed(data)
        else:
            return self._format_default(data)

    def _format_default(self, data: ParsedWeather) -> str:
        """标准格式（推荐）"""
        lines = [
            f"【{data.city}天气预报】",
            f"省份：{data.province}",
            f"更新时间：{data.update_time}",
            ""
        ]

        # 当前实况
        if data.current.temp:
            lines.append("【当前实况】")
            lines.append(f"温度：{data.current.temp}")
            if data.current.weather:
                lines.append(f"天气：{data.current.weather}")
            if data.current.wind_direction and data.current.wind_level:
                lines.append(f"风力：{data.current.wind_direction}{data.current.wind_level}")
            if data.current.humidity:
                lines.append(f"湿度：{data.current.humidity}")
            if data.current.aqi:
                lines.append(f"空气：{data.current.aqi}")
            lines.append("")

        # 7天预报
        if data.forecast_7d:
            lines.append("【未来7天】")
            for day in data.forecast_7d[:7]:
                date = day.date.replace('\n', ' ')
                weather = day.day_weather if day.day_weather == day.night_weather else f"{day.day_weather}转{day.night_weather}"
                temp = f"{day.temp_low}~{day.temp_high}" if day.temp_low else day.temp_high
                wind = f"{day.day_wind_dir}{day.day_wind_level}"
                lines.append(f"{date}: {weather} {temp} {wind}")
            lines.append("")

        # 预警
        if data.alerts:
            lines.append("【预警信息】")
            for alert in data.alerts:
                lines.append(f"⚠️ {alert}")

        return "\n".join(lines)

    def _format_simple(self, data: ParsedWeather) -> str:
        """简洁格式"""
        lines = [f"{data.city}："]

        if data.current.temp:
            lines.append(f"现在{data.current.temp}，{data.current.weather or '晴'}")

        if data.forecast_7d:
            tomorrow = data.forecast_7d[1] if len(data.forecast_7d) > 1 else None
            if tomorrow:
                lines.append(f"明天{tomorrow.temp_low}~{tomorrow.temp_high}，{tomorrow.day_weather}")

        return "，".join(lines)

    def _format_detailed(self, data: ParsedWeather) -> str:
        """详细格式（含24小时预报）"""
        lines = [self._format_default(data)]

        # 添加24小时预报
        if data.forecast_24h:
            lines.append("\n【24小时逐小时预报】")
            for hour in data.forecast_24h[:8]:  # 只显示前8小时
                lines.append(
                    f"{hour.time}: {hour.temp} "
                    f"{hour.wind_direction}{hour.wind_speed} "
                    f"湿度{hour.humidity}"
                )

        return "\n".join(lines)

    def _format_json(self, data: ParsedWeather) -> Dict:
        """JSON格式（供程序使用）"""
        return {
            "city": data.city,
            "province": data.province,
            "update_time": data.update_time,
            "current": {
                "temp": data.current.temp,
                "weather": data.current.weather,
                "humidity": data.current.humidity,
                "wind_direction": data.current.wind_direction,
                "wind_level": data.current.wind_level,
                "aqi": data.current.aqi
            },
            "forecast_7d": [
                {
                    "date": d.date,
                    "weather_day": d.day_weather,
                    "weather_night": d.night_weather,
                    "temp_high": d.temp_high,
                    "temp_low": d.temp_low,
                    "wind": f"{d.day_wind_dir}{d.day_wind_level}"
                }
                for d in data.forecast_7d
            ],
            "alerts": data.alerts
        }