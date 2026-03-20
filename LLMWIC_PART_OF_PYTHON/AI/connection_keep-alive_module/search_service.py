"""
search_service.py - 增强搜索 HTTP API 服务
基于 Flask 提供 RESTful 接口，调用增强搜索模块返回 JSON 结果。
"""

import sys
import atexit
from flask import Flask, request, jsonify

# 确保可以导入自定义模块（根据你的项目结构调整路径）
import os
sys.path.insert(0, os.path.dirname(__file__))

from search_module.enhanced_search_api import search_enhanced
from utils.driver_manager import DriverManager
from search_module.keyword_extractor import extract_keywords
app = Flask(__name__)

# 全局驱动管理器（单例，避免重复创建浏览器）
driver_manager = DriverManager()

# 注册应用退出时的清理函数
@atexit.register
def cleanup():
    if hasattr(driver_manager, 'driver') and driver_manager.driver:
        try:
            driver_manager.driver.quit()
            app.logger.info("Browser driver closed.")
        except:
            pass

@app.route('/search', methods=['POST'])
def search():
    data = request.get_json()
    if not data:
        return jsonify({'error': 'Missing JSON body'}), 400

    raw_query = data.get('query')
    if not raw_query:
        return jsonify({'error': 'Missing query'}), 400

    # 关键词提取
    processed_query = extract_keywords(raw_query)
    app.logger.info(f"原始查询: {raw_query} -> 提取后: {processed_query}")

    max_results = data.get('max_results', 3)
    fetch_content = data.get('fetch_content', True)

    try:
        # 使用处理后的查询调用增强搜索
        result = search_enhanced(
            query=processed_query,
            max_results=max_results,
            fetch_content=fetch_content,
            driver_manager=driver_manager
        )
        # 在返回结果中附加原始查询信息（可选）
        result['original_query'] = raw_query
        result['processed_query'] = processed_query
        return jsonify(result)
    except Exception as e:
        app.logger.error(f"Search error: {e}", exc_info=True)
        return jsonify({'error': str(e)}), 500

@app.route('/health', methods=['GET'])
def health():
    """健康检查接口"""
    return jsonify({
        'status': 'ok',
        'service': 'search-enhanced',
        'driver_ready': hasattr(driver_manager, 'driver') and driver_manager.driver is not None
    })

if __name__ == '__main__':
    # 可以从命令行参数指定端口，默认 5000
    port = 5000
    if len(sys.argv) > 1:
        try:
            port = int(sys.argv[1])
        except ValueError:
            print(f"Invalid port number: {sys.argv[1]}, using default 5000")
    print(f"Starting Enhanced Search Service on port {port}...")
    app.run(host='127.0.0.1', port=port, debug=False, threaded=False)