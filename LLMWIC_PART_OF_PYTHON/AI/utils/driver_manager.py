# utils/driver_manager.py
"""浏览器驱动管理模块"""
import logging
from pathlib import Path
from selenium import webdriver
from selenium.webdriver.edge.options import Options as EdgeOptions
from selenium.webdriver.edge.service import Service as EdgeService
from selenium.common.exceptions import SessionNotCreatedException, WebDriverException

from .config import Config

logger = logging.getLogger(__name__)


class DriverNotFoundError(Exception):
    """驱动未找到异常"""
    pass


class DriverManager:
    """Edge 浏览器驱动管理器"""

    # 驱动目录路径
    # 直接指定 Edge 驱动 exe 文件的完整路径
    DRIVER_PATH = Path(r"D:\个人项目\Local _Large_Model_With_Internet_Connectivity\LLMWIC_PART_OF_PYTHON\AI\driver\edgedriver_win64\edgedriver_win64\msedgedriver.exe")

    @staticmethod
    def check_edge_driver() -> tuple[bool, str, Path]:
        """
        检查 Edge 驱动是否存在

        Returns:
            tuple: (是否存在, 错误信息, 驱动完整路径)
        """
        # 检查 exe 文件是否存在
        if not DriverManager.DRIVER_PATH.exists():
            return False, f"驱动文件不存在: {DriverManager.DRIVER_PATH}", DriverManager.DRIVER_PATH

        # 检查是否是文件而不是目录
        if not DriverManager.DRIVER_PATH.is_file():
            return False, f"路径存在但不是文件: {DriverManager.DRIVER_PATH}", DriverManager.DRIVER_PATH

        # 检查文件扩展名
        if DriverManager.DRIVER_PATH.suffix.lower() != ".exe":
            return False, f"文件不是 exe 格式: {DriverManager.DRIVER_PATH.suffix}", DriverManager.DRIVER_PATH

        return True, "驱动检查通过", DriverManager.DRIVER_PATH

    @staticmethod
    def get_driver():
        """获取 Edge 浏览器驱动实例"""
        # 验证浏览器类型
        if Config.BROWSER.lower() != "edge":
            raise DriverNotFoundError(
                f"当前仅支持 Edge 浏览器，配置文件中 BROWSER='{Config.BROWSER}' 无效"
            )

        # 检查驱动
        exists, msg, driver_path = DriverManager.check_edge_driver()

        if not exists:
            error_msg = (
                f"\n{'=' * 60}\n"
                f"❌ Edge 驱动检查失败！\n\n"
                f"📁 预期驱动路径: {driver_path}\n"
                f"🔍 检查结果: {msg}\n\n"
                f"📋 目录结构要求:\n"
                f"{DriverManager.DRIVER_DIR}\n"
                f"└── win64/\n"
                f"    └── {DriverManager.EDGE_DRIVER_NAME}\n\n"
                f"🔧 请下载驱动并放到上述位置:\n"
                f"https://developer.microsoft.com/en-us/microsoft-edge/tools/webdriver/\n"
                f"{'=' * 60}\n"
            )
            logger.error(error_msg)
            raise DriverNotFoundError(f"缺少 Edge 驱动: {msg}")

        # 启动浏览器
        try:
            logger.info(f"驱动检查通过: {driver_path}")

            options = EdgeOptions()
            if Config.HEADLESS:
                options.add_argument("--headless=new")
            options.add_argument("--disable-gpu")
            options.add_argument("--no-sandbox")
            options.add_argument("--disable-dev-shm-usage")

            # 禁用自动化特征
            options.add_experimental_option("excludeSwitches", ["enable-automation"])
            options.add_experimental_option("useAutomationExtension", False)

            service = EdgeService(executable_path=str(driver_path))
            driver = webdriver.Edge(service=service, options=options)

            driver.implicitly_wait(Config.IMPLICIT_WAIT)
            driver.maximize_window()

            logger.info("✅ Edge 浏览器启动成功")
            return driver

        except SessionNotCreatedException as e:
            error_msg = (
                f"\n{'=' * 60}\n"
                f"❌ 驱动版本与浏览器版本不匹配！\n\n"
                f"错误信息: {str(e)}\n\n"
                f"💡 请更新驱动版本:\n"
                f"Edge 版本: edge://settings/help\n"
                f"驱动下载: https://developer.microsoft.com/en-us/microsoft-edge/tools/webdriver/\n"
                f"{'=' * 60}\n"
            )
            logger.error(error_msg)
            raise DriverNotFoundError(f"驱动版本不匹配: {str(e)}") from e

        except WebDriverException as e:
            logger.error(f"WebDriver 异常: {str(e)}")
            raise DriverNotFoundError(f"驱动启动失败: {str(e)}") from e

        except Exception as e:
            logger.error(f"未知错误: {str(e)}", exc_info=True)
            raise DriverNotFoundError(f"启动失败: {str(e)}") from e