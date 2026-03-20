const chatBox = document.getElementById('chatBox');
const userInput = document.getElementById('userInput');
const sendBtn = document.getElementById('sendBtn');
const webSearchToggle = document.getElementById('webSearchToggle');
const modeIndicator = document.getElementById('modeIndicator');

// 配置
const API_URL = '/v1/chat/completions';
const MODEL = 'deepseek-r1:7b'; // 根据你的实际模型名称调整

// 状态变量
let isWebSearchEnabled = false;
let isSending = false;

// 【新增】用于存储会话 ID，初始为 null
let currentSessionId = null;

/**
 * 核心方法：添加消息并解析 <think> 标签
 * (保持原有逻辑不变)
 */
function addMessage(role, content, thinking = null) {
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${role}`;

    const avatar = document.createElement('div');
    avatar.className = 'avatar';
    avatar.textContent = role === 'user' ? '👤' : (role === 'assistant' ? '🤖' : '⚙️');

    const contentDiv = document.createElement('div');
    contentDiv.className = 'content';

    // 简单的文本格式化
    function formatText(text) {
        if (!text) return '';
        let formatted = text.replace(/\n/g, '<br>');
        // 简单的链接识别
        formatted = formatted.replace(/(https?:\/\/[^\s]+)/g, '<a href="$1" target="_blank" style="color: inherit; text-decoration: underline;">$1</a>');
        return formatted;
    }

    // 解析 <think> 标签
    let extractedThinking = null;
    let extractedAnswer = content;

    // 使用正则提取 <think>...</think> 内容
    const thinkMatch = content.match(/<think>([\s\S]*?)<\/think>/i);
    if (thinkMatch) {
        extractedThinking = thinkMatch[1].trim();
        // 移除原始内容中的 think 标签部分
        extractedAnswer = content.replace(/<think>[\s\S]*?<\/think>/i, '').trim();
    }

    // 优先使用提取出的思考内容，如果没有则使用传入的 thinking 参数
    const displayThinking = extractedThinking || thinking;

    if (displayThinking && displayThinking !== '') {
        const thinkingDiv = document.createElement('div');
        thinkingDiv.className = 'thinking';
        thinkingDiv.innerHTML = formatText(displayThinking);
        contentDiv.appendChild(thinkingDiv);
    }

    const answerDiv = document.createElement('div');
    answerDiv.className = 'answer';
    // 如果提取后答案为空的特殊情况处理
    const finalAnswer = extractedAnswer || content;
    answerDiv.innerHTML = formatText(finalAnswer);
    contentDiv.appendChild(answerDiv);

    messageDiv.appendChild(avatar);
    messageDiv.appendChild(contentDiv);
    chatBox.appendChild(messageDiv);

    // 滚动到底部
    chatBox.scrollTop = chatBox.scrollHeight;
}

/**
 * 更新 UI 状态：按钮样式和指示器
 */
function updateUIState() {
    if (isWebSearchEnabled) {
        // 激活状态
        webSearchToggle.classList.add('active');
        webSearchToggle.innerHTML = '🌍'; // 切换到地球图标
        webSearchToggle.title = '关闭联网搜索';

        modeIndicator.textContent = '当前模式：联网搜索 🔍';
        modeIndicator.classList.add('show', 'web-active');
    } else {
        // 默认状态
        webSearchToggle.classList.remove('active');
        webSearchToggle.innerHTML = '🌐'; // 切换回卫星图标
        webSearchToggle.title = '开启联网搜索';

        modeIndicator.textContent = '当前模式：本地对话';
        modeIndicator.classList.remove('web-active');

        // 如果是本地模式，2秒后自动隐藏指示器，避免遮挡
        setTimeout(() => {
            if (!isWebSearchEnabled) {
                modeIndicator.classList.remove('show');
            }
        }, 2000);
    }
}

// 绑定联网搜索按钮点击事件
webSearchToggle.addEventListener('click', () => {
    isWebSearchEnabled = !isWebSearchEnabled;
    updateUIState();
    userInput.focus(); // 点击后保持焦点在输入框
});

// 自动调整输入框高度
userInput.addEventListener('input', function() {
    this.style.height = 'auto';
    this.style.height = (this.scrollHeight) + 'px';
    if (this.value === '') this.style.height = 'auto';
});

// 监听 Enter 发送
userInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
    }
});

// 发送消息主逻辑
async function sendMessage() {
    if (isSending) return;

    const originalInput = userInput.value.trim();
    if (!originalInput) return;

    isSending = true;
    sendBtn.disabled = true;
    sendBtn.textContent = '...';

    // 构造发送给后端的消息
    let finalMessage = originalInput;

    // 如果开启了联网搜索，且用户没有手动输入前缀，则自动添加
    // if (isWebSearchEnabled && !originalInput.startsWith('联网搜索')) {
    //     finalMessage = '联网搜索 ' + originalInput;
    // }
    //以上为废弃方案，但保留

    // 清空输入框
    userInput.value = '';
    userInput.style.height = 'auto';

    // 在界面上显示用户消息（显示原始输入，不带前缀，体验更好）
    addMessage('user', originalInput);

    try {
        // 【关键修改】在请求体中携带 currentSessionId
        const response = await fetch(API_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                model: MODEL,
                messages: [{ role: 'user', content: finalMessage }],
                sessionId: currentSessionId,   // 带上存储的会话ID（首次为null）
                stream: false
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const data = await response.json();
        const aiContent = data.choices?.[0]?.message?.content || "无回复内容";

        // 【关键修改】更新会话ID（如果后端返回了）
        if (data.sessionId) {
            currentSessionId = data.sessionId;
            console.log('当前会话ID已更新为:', currentSessionId);
        }

        addMessage('assistant', aiContent);

    } catch (error) {
        console.error('Error:', error);
        addMessage('system', `❌ 请求失败: ${error.message}`);
    } finally {
        isSending = false;
        sendBtn.disabled = false;
        sendBtn.textContent = '发送';
        userInput.focus();
    }
}

// 初始化
updateUIState();
modeIndicator.classList.remove('show'); // 初始隐藏指示器