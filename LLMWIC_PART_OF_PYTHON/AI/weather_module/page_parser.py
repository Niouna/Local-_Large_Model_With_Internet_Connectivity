"""
天气页面HTML解析模块
解析7天预报、24小时预报、当前实况
"""

import re
from typing import Dict, List, Optional
from dataclasses import dataclass
from bs4 import BeautifulSoup


@dataclass
class CurrentWeather:
    """当前实况"""
    temp: str = ""  # 温度
    weather: str = ""  # 天气状况
    humidity: str = ""  # 湿度
    wind_direction: str = ""  # 风向
    wind_level: str = ""  # 风力
    pressure: str = ""  # 气压
    precipitation: str = ""  # 降水量
    feel_temp: str = ""  # 体感温度
    aqi: str = ""  # 空气质量
    comfort: str = ""  # 舒适度
    update_time: str = ""  # 更新时间


@dataclass
class DayForecast:
    """单日预报"""
    date: str  # 02/20 周五
    day_weather: str  # 白天天气
    night_weather: str  # 夜间天气
    temp_high: str  # 最高温
    temp_low: str  # 最低温
    day_wind_dir: str  # 白天风向
    day_wind_level: str  # 白天风力
    night_wind_dir: str  # 夜间风向
    night_wind_level: str  # 夜间风力


@dataclass
class HourForecast:
    """逐小时预报"""
    time: str  # 23:00
    temp: str  # 8.7℃
    weather: str  # 天气图标（从img提取）
    precipitation: str  # 降水量
    wind_speed: str  # 风速
    wind_direction: str  # 风向
    humidity: str  # 湿度
    pressure: str = ""  # 气压（可选）


@dataclass
class ParsedWeather:
    """完整解析结果"""
    city: str
    province: str
    update_time: str
    current: CurrentWeather
    forecast_7d: List[DayForecast]
    forecast_24h: List[HourForecast]
    alerts: List[str]


class WeatherPageParser:
    """天气页面解析器"""

    def __init__(self, html: str):
        self.html = html
        self.soup = BeautifulSoup(html, 'html.parser')

    def parse(self) -> ParsedWeather:
        """解析完整天气数据"""
        return ParsedWeather(
            city=self._parse_city(),
            province=self._parse_province(),
            update_time=self._parse_update_time(),
            current=self._parse_current(),
            forecast_7d=self._parse_7d_forecast(),
            forecast_24h=self._parse_24h_forecast(),
            alerts=self._parse_alerts()
        )

    def _parse_city(self) -> str:
        """解析城市名"""
        # 从breadcrumb
        breadcrumb = self.soup.find('ol', class_='breadcrumb')
        if breadcrumb:
            active = breadcrumb.find('li', class_='active')
            if active:
                return active.text.replace('天气预报', '').strip()

        # 从title
        title = self.soup.find('title')
        if title:
            match = re.search(r'(.+?)-天气预报', title.text)
            if match:
                return match.group(1)

        return ""

    def _parse_province(self) -> str:
        """解析省份"""
        breadcrumb = self.soup.find('ol', class_='breadcrumb')
        if breadcrumb:
            links = breadcrumb.find_all('a')
            for link in links:
                text = link.text.strip()
                if '省' in text or ('市' in text and len(text) <= 4):
                    return text
        return ""

    def _parse_update_time(self) -> str:
        """解析发布时间"""
        # 从页面文本
        text_match = re.search(r'发布时间[：:]\s*(\d{2}-\d{2}\s+\d{2}:\d{2})', self.html)
        if text_match:
            return text_match.group(1)

        # 从div（Selenium渲染后）
        time_div = self.soup.find('div', id='realPublishTime')
        if time_div:
            text = time_div.text.strip()
            if text and text != '&nbsp;':
                return text

        return ""

    def _parse_current(self) -> CurrentWeather:
        """解析当前实况（Selenium渲染后）"""
        current = CurrentWeather()

        # 温度
        temp_div = self.soup.find('div', id='realTemperature')
        if temp_div:
            current.temp = temp_div.text.strip()

        # 湿度
        humidity_div = self.soup.find('div', id='realHumidity')
        if humidity_div:
            current.humidity = humidity_div.text.strip()

        # 风向风力
        wind_dir_div = self.soup.find('div', id='realWindDirect')
        wind_power_div = self.soup.find('div', id='realWindPower')
        if wind_dir_div:
            current.wind_direction = wind_dir_div.text.strip()
        if wind_power_div:
            current.wind_level = wind_power_div.text.strip()

        # 降水量
        rain_div = self.soup.find('div', id='realRain')
        if rain_div:
            current.precipitation = rain_div.text.strip()

        # 体感温度
        feel_div = self.soup.find('div', id='realFeelst')
        if feel_div:
            current.feel_temp = feel_div.text.strip()

        # 空气质量
        aqi_span = self.soup.find('span', id='aqi')
        if aqi_span:
            current.aqi = aqi_span.text.strip()

        # 舒适度
        comfort_span = self.soup.find('span', id='realIcomfort')
        if comfort_span:
            current.comfort = comfort_span.text.strip()

        # 更新时间
        current.update_time = self._parse_update_time()

        return current

    def _parse_7d_forecast(self) -> List[DayForecast]:
        """解析7天预报"""
        forecasts = []

        container = self.soup.find('div', id='day7')
        if not container:
            return forecasts

        days = container.find_all('div', class_='weather')

        for day in days:
            try:
                # 日期
                date_div = day.find('div', class_='date')
                date = date_div.text.strip() if date_div else ""

                # 天气状况（白天和夜间各有一个）
                desc_divs = day.find_all('div', class_='desc')
                day_weather = desc_divs[0].text.strip() if len(desc_divs) > 0 else ""
                night_weather = desc_divs[1].text.strip() if len(desc_divs) > 1 else ""

                # 风向风力
                windd_divs = day.find_all('div', class_='windd')
                winds_divs = day.find_all('div', class_='winds')

                day_wind_dir = windd_divs[0].text.strip() if len(windd_divs) > 0 else ""
                day_wind_level = winds_divs[0].text.strip() if len(winds_divs) > 0 else ""
                night_wind_dir = windd_divs[1].text.strip() if len(windd_divs) > 1 else ""
                night_wind_level = winds_divs[1].text.strip() if len(winds_divs) > 1 else ""

                # 温度（提取所有tmp div）
                temp_divs = day.find_all('div', class_=re.compile(r'tmp_lte_\d+'))
                temps = [t.text.strip() for t in temp_divs]

                # 通常第一个是高温，第二个是低温，但需要判断
                temp_high = ""
                temp_low = ""
                for t in temps:
                    # 提取数字判断
                    num_match = re.search(r'(\d+)', t)
                    if num_match:
                        val = int(num_match.group(1))
                        if val >= 20:  # 假设20度以上为高温
                            temp_high = t
                        else:
                            temp_low = t

                # 如果只有一个温度，默认是高温
                if not temp_high and temps:
                    temp_high = temps[0]
                if len(temps) > 1 and not temp_low:
                    temp_low = temps[1]

                forecasts.append(DayForecast(
                    date=date,
                    day_weather=day_weather,
                    night_weather=night_weather,
                    temp_high=temp_high,
                    temp_low=temp_low,
                    day_wind_dir=day_wind_dir,
                    day_wind_level=day_wind_level,
                    night_wind_dir=night_wind_dir,
                    night_wind_level=night_wind_level
                ))

            except Exception as e:
                print(f"解析单日预报失败: {e}")
                continue

        return forecasts

    def _parse_24h_forecast(self) -> List[HourForecast]:
        """解析24小时逐小时预报"""
        forecasts = []

        container = self.soup.find('div', id='hourValues')
        if not container:
            return forecasts

        # 每个dayX div包含8个时间点（3小时间隔）
        day_divs = container.find_all('div', id=re.compile(r'day\d+'))

        for day_div in day_divs:
            hour_divs = day_div.find_all('div', class_='hour3')

            for hour in hour_divs:
                try:
                    divs = hour.find_all('div')
                    if len(divs) < 8:
                        continue

                    time_str = divs[0].text.strip()

                    # 天气图标（从img提取alt或title）
                    img = hour.find('img')
                    weather = ""
                    if img:
                        weather = img.get('alt', '') or img.get('title', '')

                    precipitation = divs[2].text.strip()  # 第3个div是降水
                    temp = divs[3].text.strip()  # 第4个div是温度
                    wind_speed = divs[4].text.strip()  # 第5个div是风速
                    wind_direction = divs[5].text.strip()  # 第6个div是风向
                    # 第7个div是气压（hide）
                    humidity = divs[7].text.strip()  # 第8个div是湿度

                    forecasts.append(HourForecast(
                        time=time_str,
                        temp=temp,
                        weather=weather,
                        precipitation=precipitation,
                        wind_speed=wind_speed,
                        wind_direction=wind_direction,
                        humidity=humidity
                    ))

                except Exception as e:
                    continue

        return forecasts

    def _parse_alerts(self) -> List[str]:
        """解析预警信息"""
        alerts = []

        # 从alarmmsg div
        alarm_div = self.soup.find('div', id='realWarn')
        if alarm_div:
            text = alarm_div.text.strip()
            if text and text not in ['&nbsp;', '']:
                alerts.append(text)

        return alerts