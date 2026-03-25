// --- DOM Elements ---
const chatMessages = document.getElementById('chatMessages');
const userInput = document.getElementById('messageInput');
const sendBtn = document.getElementById('sendBtn');
const webSearchToggle = document.getElementById('webSearchToggle');
const gpuModelSelect = document.getElementById('gpuModelSelect');
const cpuModelSelect = document.getElementById('cpuModelSelect');

// --- Configuration ---
const API_URL = '/v1/chat/completions';

// --- State Variables ---
let isWebSearchEnabled = false;
let isSending = false;
let currentSessionId = null;
let currentModel = null; // 当前选中的模型
let currentModelType = null; // 记录当前模型来自哪个选择器（gpu 或 cpu）

// --- Initialization ---
document.addEventListener('DOMContentLoaded', () => {
    loadModels(); // 启动时加载模型 -> 确保这行存在
    initEventListeners();
    updateUIState();

    // 初始化侧边栏菜单点击事件 -> 这部分是新增的
    const menuItems = document.querySelectorAll('.menu-item');
    menuItems.forEach(item => {
        item.addEventListener('click', () => {
            const action = item.getAttribute('data-action');

            if (action === 'redirect') {
                // 如果是跳转类型，则直接跳转
                const url = item.getAttribute('data-url');
                if (url) {
                    window.open(url, '_blank'); // 在新标签页打开
                    // 或者使用 window.location.href = url; 在当前标签页打开
                }
            } else {
                // 否则，按原有逻辑处理页面切换
                // 移除所有 active 类
                menuItems.forEach(i => i.classList.remove('active'));
                // 添加当前 active
                item.classList.add('active');

                // 获取页面 ID
                const pageId = item.getAttribute('data-page');
                const page = document.getElementById(`${pageId}-page`);

                // 关键修复：检查页面元素是否存在，防止报错
                if (page) {
                    // 显示对应页面
                    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
                    page.classList.add('active');
                } else {
                    console.warn(`未找到ID为 '${pageId}-page' 的页面元素`);
                }
            }
        });
    });
});

// --- Core Logic ---

// 加载模型列表并分别填充 GPU 和 CPU 下拉框 -> 确保这个函数存在
async function loadModels() {
    try {
        const res = await fetch('/api/models');
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const models = await res.json();

        // 清空两个下拉框
        gpuModelSelect.innerHTML = '';
        cpuModelSelect.innerHTML = '';

        // 按端口分组
        const gpuModels = models.filter(m => m.port === 11434);
        const cpuModels = models.filter(m => m.port === 11435);

        // 填充 GPU 下拉框
        if (gpuModels.length === 0) {
            const option = document.createElement('option');
            option.value = '';
            option.textContent = '无可用模型';
            gpuModelSelect.appendChild(option);
            gpuModelSelect.disabled = true;
        } else {
            gpuModels.forEach(model => {
                const option = document.createElement('option');
                option.value = model.name;
                option.textContent = model.displayName;
                gpuModelSelect.appendChild(option);
            });
            gpuModelSelect.disabled = false;
        }

        // 填充 CPU 下拉框
        if (cpuModels.length === 0) {
            const option = document.createElement('option');
            option.value = '';
            option.textContent = '无可用模型';
            cpuModelSelect.appendChild(option);
            cpuModelSelect.disabled = true;
        } else {
            cpuModels.forEach(model => {
                const option = document.createElement('option');
                option.value = model.name;
                option.textContent = model.displayName;
                cpuModelSelect.appendChild(option);
            });
            cpuModelSelect.disabled = false;
        }

        // 尝试恢复上次选择的模型
        const savedModel = localStorage.getItem('selectedModel');
        const savedType = localStorage.getItem('selectedModelType');
        if (savedModel && savedType) {
            if (savedType === 'gpu' && gpuModels.some(m => m.name === savedModel)) {
                gpuModelSelect.value = savedModel;
                currentModel = savedModel;
                currentModelType = 'gpu';
            } else if (savedType === 'cpu' && cpuModels.some(m => m.name === savedModel)) {
                cpuModelSelect.value = savedModel;
                currentModel = savedModel;
                currentModelType = 'cpu';
            } else {
                // 如果保存的模型不可用，默认选择第一个可用的模型
                setDefaultModel(gpuModels, cpuModels);
            }
        } else {
            setDefaultModel(gpuModels, cpuModels);
        }
    } catch (err) {
        console.error('加载模型列表失败', err);
        // 降级：显示默认模型
        const defaultOption = document.createElement('option');
        defaultOption.value = 'deepseek-r1:7b';
        defaultOption.textContent = 'deepseek-r1:7b';
        gpuModelSelect.appendChild(defaultOption);
        gpuModelSelect.disabled = false;
        currentModel = 'deepseek-r1:7b';
        currentModelType = 'gpu';
        gpuModelSelect.value = currentModel;
    }
}

// 设置默认模型（优先 GPU，其次 CPU）
function setDefaultModel(gpuModels, cpuModels) {
    if (gpuModels.length > 0) {
        currentModel = gpuModels[0].name;
        currentModelType = 'gpu';
        gpuModelSelect.value = currentModel;
    } else if (cpuModels.length > 0) {
        currentModel = cpuModels[0].name;
        currentModelType = 'cpu';
        cpuModelSelect.value = currentModel;
    }
}

// 初始化所有事件监听器
function initEventListeners() {
    // 监听 GPU 下拉框变化
    gpuModelSelect.addEventListener('change', () => {
        if (gpuModelSelect.disabled) return;
        currentModel = gpuModelSelect.value;
        currentModelType = 'gpu';
        localStorage.setItem('selectedModel', currentModel);
        localStorage.setItem('selectedModelType', 'gpu');
        currentSessionId = null; // 切换模型时重置会话
        addSystemMessage(`已切换到 GPU 模型：${currentModel}`);
    });

    // 监听 CPU 下拉框变化
    cpuModelSelect.addEventListener('change', () => {
        if (cpuModelSelect.disabled) return;
        currentModel = cpuModelSelect.value;
        currentModelType = 'cpu';
        localStorage.setItem('selectedModel', currentModel);
        localStorage.setItem('selectedModelType', 'cpu');
        currentSessionId = null; // 切换模型时重置会话
        addSystemMessage(`已切换到 CPU 模型：${currentModel}`);
    });

    // 发送按钮
    sendBtn.addEventListener('click', sendMessage);

    // 输入框回车发送
    userInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    // 输入框自动调整高度
    userInput.addEventListener('input', autoResize);

    // 联网搜索按钮
    webSearchToggle.addEventListener('click', () => {
        isWebSearchEnabled = !isWebSearchEnabled;
        updateUIState();
        userInput.focus();
    });
}

// ... (其他代码不变) ...

async function sendMessage() {
    if (isSending) return;
    const message = userInput.value.trim();
    if (!message) return;
    if (!currentModel) {
        addSystemMessage('请先选择一个模型');
        return;
    }

    isSending = true;
    sendBtn.disabled = true;
    sendBtn.textContent = '发送中...';

    // 清空输入框
    userInput.value = '';
    autoResize.call(userInput);

    // 显示用户消息
    addMessage('user', message, currentModel);

    try {
        // 关键修改：将 isWebSearchEnabled 状态加入请求体
        const requestBody = {
            model: currentModel,
            messages: [{ role: 'user', content: message }],
            sessionId: currentSessionId,
            isWebSearchEnabled: isWebSearchEnabled, // 新增此行
            stream: false
        };
        const response = await fetch(API_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestBody)
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const data = await response.json();
        const aiContent = data.choices?.[0]?.message?.content || '无回复内容';
        if (data.sessionId) {
            currentSessionId = data.sessionId;
        }
        addMessage('assistant', aiContent, currentModel);
    } catch (error) {
        console.error('Error:', error);
        addMessage('system', `请求失败: ${error.message}`, currentModel);
    } finally {
        isSending = false;
        sendBtn.disabled = false;
        sendBtn.textContent = '发送';
        userInput.focus();
    }
}

// ... (其他代码不变) ...

// --- Message Handling ---

// 添加消息（支持 think 标签解析）
function addMessage(role, content, model = null) {
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${role}`;

    const avatar = document.createElement('div');
    avatar.className = 'avatar';
    avatar.textContent = role === 'user' ? '👤' : (role === 'assistant' ? '🤖' : '⚙️');

    const contentDiv = document.createElement('div');
    contentDiv.className = 'content';

    // 简单的文本格式化（链接识别、换行）
    function formatText(text) {
        if (!text) return '';
        let formatted = text.replace(/\n/g, '<br>');
        formatted = formatted.replace(/(https?:\/\/[^\s]+)/g, '<a href="$1" target="_blank" style="color: inherit; text-decoration: underline;">$1</a>');
        return formatted;
    }

    // 判断是否需要解析 <think> 标签（仅 assistant 且模型名包含 deepseek）
    const shouldParseThink = (role === 'assistant' && model && model.toLowerCase().includes('deepseek'));

    if (shouldParseThink) {
        // 提取 think 内容
        const thinkMatch = content.match(/<think>([\s\S]*?)<\/think>/i);
        if (thinkMatch) {
            const thinkingContent = thinkMatch[1].trim();
            const answerContent = content.replace(/<think>[\s\S]*?<\/think>/i, '').trim();

            // 添加思考区域
            const thinkingDiv = document.createElement('div');
            thinkingDiv.className = 'thinking';
            thinkingDiv.innerHTML = formatText(thinkingContent);
            contentDiv.appendChild(thinkingDiv);

            // 添加答案区域
            const answerDiv = document.createElement('div');
            answerDiv.className = 'answer';
            answerDiv.innerHTML = formatText(answerContent || '（无回答）');
            contentDiv.appendChild(answerDiv);
        } else {
            // 没有 think 标签，直接显示内容
            const answerDiv = document.createElement('div');
            answerDiv.className = 'answer';
            answerDiv.innerHTML = formatText(content);
            contentDiv.appendChild(answerDiv);
        }
    } else {
        // 非 deepseek 模型，直接显示全部内容
        const answerDiv = document.createElement('div');
        answerDiv.className = 'answer';
        answerDiv.innerHTML = formatText(content);
        contentDiv.appendChild(answerDiv);
    }

    messageDiv.appendChild(avatar);
    messageDiv.appendChild(contentDiv);
    chatMessages.appendChild(messageDiv);
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

// 添加系统消息（辅助）
function addSystemMessage(text) {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message system';
    const avatar = document.createElement('div');
    avatar.className = 'avatar';
    avatar.textContent = '⚙️';
    const contentDiv = document.createElement('div');
    contentDiv.className = 'content';
    contentDiv.innerHTML = text;
    messageDiv.appendChild(avatar);
    messageDiv.appendChild(contentDiv);
    chatMessages.appendChild(messageDiv);
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

// --- Utility Functions ---

function autoResize() {
    this.style.height = 'auto';
    this.style.height = Math.min(this.scrollHeight, 150) + 'px'; // 限制最大高度
    if (this.value === '') this.style.height = 'auto';
}

function updateUIState() {
    if (isWebSearchEnabled) {
        webSearchToggle.classList.add('active');
        webSearchToggle.innerHTML = '🌍';
        webSearchToggle.title = '关闭联网搜索';
    } else {
        webSearchToggle.classList.remove('active');
        webSearchToggle.innerHTML = '🌐';
        webSearchToggle.title = '开启联网搜索';
    }
}