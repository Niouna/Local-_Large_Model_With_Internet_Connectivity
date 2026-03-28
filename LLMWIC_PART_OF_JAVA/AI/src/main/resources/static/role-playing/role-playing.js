// ======================== 全局游戏状态 ========================
let currentSessionId = null;
let currentModel = 'deepseek-r1:7b';
let currentMode = 'NORMAL';
let isGenerating = false;
let abortController = null;
let lastUserInput = '';
let lastMode = 'NORMAL';
let currentEntities = {};
let currentEntityId = 'player';

// DOM 元素
let narrativeTextEl, messageInput, sendBtn, gameModelSelect, regenerateBtn;
let stopGenerateBtn, savePointBtn, loadPointBtn, refreshStateBtn, resetGameBtn;
let entityCardContainer, entityTabs, entityCard, loadingState;

// ========== 初始化函数 ==========
document.addEventListener('DOMContentLoaded', () => {
    // 获取DOM元素
    narrativeTextEl = document.getElementById('narrativeText');
    messageInput = document.getElementById('messageInput');
    sendBtn = document.getElementById('sendBtn');
    gameModelSelect = document.getElementById('gameModelSelect');
    regenerateBtn = document.getElementById('regenerateBtn');
    stopGenerateBtn = document.getElementById('stopGenerateBtn');
    savePointBtn = document.getElementById('savePointBtn');
    loadPointBtn = document.getElementById('loadPointBtn');
    refreshStateBtn = document.getElementById('refreshStateBtn');
    resetGameBtn = document.getElementById('resetGameBtn');
    entityCardContainer = document.getElementById('entityCardContainer');
    entityTabs = document.getElementById('entityTabs');
    entityCard = document.getElementById('entityCard');
    loadingState = document.getElementById('loadingState');

    // 初始化会话
    currentSessionId = localStorage.getItem('gameSessionId');
    if (!currentSessionId) {
        currentSessionId = 'game_' + Date.now() + '_' + Math.random().toString(36).substr(2, 8);
        localStorage.setItem('gameSessionId', currentSessionId);
    }

    const savedModel = localStorage.getItem('gameModel');
    if (savedModel) {
        gameModelSelect.value = savedModel;
        currentModel = savedModel;
    }

    // 加载游戏状态
    loadGameState();

    // 初始化事件监听
    initEventListeners();
    initSidebarMenu();
    initModals();
});

function initEventListeners() {
    sendBtn.addEventListener('click', sendAction);
    messageInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendAction();
        }
    });

    gameModelSelect.addEventListener('change', () => {
        currentModel = gameModelSelect.value;
        localStorage.setItem('gameModel', currentModel);
    });

    document.querySelectorAll('.mode-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.mode-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            currentMode = btn.dataset.mode;
        });
    });
    const defaultBtn = document.querySelector('.mode-btn[data-mode="NORMAL"]');
    if (defaultBtn) defaultBtn.classList.add('active');

    regenerateBtn.addEventListener('click', regenerate);
    stopGenerateBtn.addEventListener('click', stopGeneration);
    savePointBtn.addEventListener('click', openSaveModal);
    loadPointBtn.addEventListener('click', openLoadModal);
    refreshStateBtn.addEventListener('click', loadGameState);
    resetGameBtn.addEventListener('click', resetGame);

    // 新增：加载世界按钮
    const loadSnapshotBtn = document.querySelector('.load-snapshot-btn');
    if (loadSnapshotBtn) {
        loadSnapshotBtn.addEventListener('click', openLoadWorldModal);
    }
}

function initModals() {
    const saveModal = document.getElementById('saveModal');
    const loadModal = document.getElementById('loadModal');
    const closeBtns = document.querySelectorAll('.close');

    closeBtns.forEach(btn => {
        btn.onclick = () => {
            if (saveModal) saveModal.style.display = 'none';
            if (loadModal) loadModal.style.display = 'none';
        };
    });

    window.onclick = (event) => {
        if (event.target === saveModal) saveModal.style.display = 'none';
        if (event.target === loadModal) loadModal.style.display = 'none';
    };
}

// ========== 游戏核心功能 ==========
async function loadGameState() {
    try {
        loadingState.style.display = 'block';
        const response = await fetch(`/v1/game/state/${currentSessionId}`);
        if (response.ok) {
            const state = await response.json();
            updateEntityDisplay(state);
            loadingState.style.display = 'none';
            entityCardContainer.style.display = 'block';
        } else {
            const mockState = {
                playerName: "冒险者", currentLocation: "迷雾森林", turnNumber: 3,
                attributes: {力量:12,敏捷:10,智力:14,魅力:9},
                inventory: ["生锈的剑","魔法面包"],
                activeForeshadowing: ["古老的预言"],
                npcs: [{id:"npc_1",name:"神秘老人",location:"森林小屋",attributes:{智慧:18},relationship:5}]
            };
            updateEntityDisplay(mockState);
            loadingState.style.display = 'none';
            entityCardContainer.style.display = 'block';
        }
    } catch (error) {
        console.error('加载状态失败', error);
        loadingState.innerHTML = '加载失败: ' + error.message;
    }
}

function updateEntityDisplay(state) {
    currentEntities = {};
    currentEntities.player = {
        id: 'player',
        name: state.playerName || '冒险者',
        type: 'player',
        location: state.currentLocation || '起始地点',
        attributes: state.attributes || {力量:10,敏捷:10,智力:10,魅力:10},
        inventory: state.inventory || [],
        foreshadowing: state.activeForeshadowing || [],
        turnNumber: state.turnNumber || 0
    };

    if (state.npcs && Array.isArray(state.npcs)) {
        state.npcs.forEach(npc => {
            currentEntities[npc.id] = { ...npc, type: 'npc' };
        });
    }

    renderEntityTabs();
    renderEntityCard(currentEntityId);
}

function renderEntityTabs() {
    entityTabs.innerHTML = '';
    Object.values(currentEntities).forEach(entity => {
        const btn = document.createElement('button');
        btn.className = 'entity-tab' + (entity.id === currentEntityId ? ' active' : '');
        btn.textContent = entity.type === 'player' ? '👤 ' + entity.name : '👥 ' + entity.name;
        btn.onclick = () => {
            document.querySelectorAll('.entity-tab').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            currentEntityId = entity.id;
            renderEntityCard(currentEntityId);
        };
        entityTabs.appendChild(btn);
    });
}

function renderEntityCard(entityId) {
    const e = currentEntities[entityId];
    if (!e) {
        entityCard.innerHTML = '<p>无法加载实体数据</p>';
        return;
    }

    let attrsHtml = '';
    if (e.attributes) {
        for (let [k, v] of Object.entries(e.attributes)) {
            attrsHtml += `<div class="attribute"><span class="label">${escapeHtml(k)}</span><span class="value">${v}</span></div>`;
        }
    } else {
        attrsHtml = '<div style="text-align:center; color:#94a3b8;">无属性数据</div>';
    }

    let invHtml = e.inventory?.length ?
        `<div class="badge-list">${e.inventory.map(i => `<span class="badge">📦 ${escapeHtml(i)}</span>`).join('')}</div>` :
        '<span style="color:#94a3b8;">空</span>';

    let foreHtml = e.foreshadowing?.length ?
        `<div class="badge-list">${e.foreshadowing.map(f => `<span class="badge foreshadow">🔮 ${escapeHtml(f)}</span>`).join('')}</div>` :
        '<span style="color:#94a3b8;">无</span>';

    entityCard.innerHTML = `
        <h3>${e.type === 'player' ? '👤' : '👥'} ${escapeHtml(e.name)}</h3>
        <div class="entity-details">
            <p><strong>📍 位置:</strong> ${escapeHtml(e.location)}</p>
            <p><strong>🎒 背包:</strong> ${invHtml}</p>
            <p><strong>🔮 伏笔:</strong> ${foreHtml}</p>
            <p><strong>📜 剧情进度:</strong> 第 ${e.turnNumber || 0} 轮</p>
        </div>
        <div class="entity-attributes">${attrsHtml}</div>
    `;
}

async function sendAction() {
    if (isGenerating) {
        alert('正在生成中，请稍后或点击停止');
        return;
    }

    const userInput = messageInput.value.trim();
    if (!userInput) return;

    lastUserInput = userInput;
    lastMode = currentMode;

    messageInput.value = '';
    appendUserMessage(userInput);
    await generateNarrative(userInput, currentMode);
}

async function generateNarrative(userInput, mode) {
    isGenerating = true;
    sendBtn.disabled = true;
    regenerateBtn.disabled = true;
    stopGenerateBtn.style.display = 'inline-block';

    showLoadingIndicator();
    abortController = new AbortController();

    try {
        const response = await fetch('/v1/game/stream', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                sessionId: currentSessionId,
                userInput: userInput,
                mode: mode,
                model: currentModel,
                stream: true
            }),
            signal: abortController.signal
        });

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() || '';

            for (const line of lines) {
                const trimmedLine = line.trim();
                if (trimmedLine.startsWith('data:')) {
                    try {
                        const jsonStr = trimmedLine.substring(5).trim();
                        if (jsonStr && jsonStr !== '[DONE]') {
                            const data = JSON.parse(jsonStr);
                            if (data.narrative) {
                                removeLoadingIndicator();
                                appendNarrative(data.narrative);
                            }
                            if (data.options) showOptions(data.options);
                            if (data.stateChanges) loadGameState();
                            if (data.error) appendSystemMessage('❌ ' + data.error);
                        }
                    } catch (e) {
                        console.warn('解析数据失败:', trimmedLine);
                    }
                }
            }
        }
    } catch (error) {
        if (error.name === 'AbortError') {
            appendSystemMessage('⏸️ 已停止生成');
        } else {
            appendSystemMessage(`❌ 生成失败: ${error.message}`);
        }
    } finally {
        isGenerating = false;
        sendBtn.disabled = false;
        regenerateBtn.disabled = false;
        stopGenerateBtn.style.display = 'none';
        hideLoadingIndicator();
        abortController = null;
    }
}

// UI辅助函数
function appendNarrative(text) {
    removeLoadingIndicator();
    let contentDiv = narrativeTextEl;
    if (contentDiv.innerHTML.includes('欢迎来到 LLMWIC_Role_playing')) {
        contentDiv.innerHTML = '';
    }
    const narrativeDiv = document.createElement('div');
    narrativeDiv.className = 'narrative-entry';
    narrativeDiv.style.marginBottom = '16px';
    narrativeDiv.style.borderLeft = '3px solid #10b981';
    narrativeDiv.style.paddingLeft = '12px';
    narrativeDiv.style.lineHeight = '1.8';
    narrativeDiv.innerHTML = escapeHtml(text).replace(/\n/g, '<br>');
    contentDiv.appendChild(narrativeDiv);
    scrollToBottom();
}

function appendUserMessage(text) {
    let contentDiv = narrativeTextEl;
    if (contentDiv.innerHTML.includes('欢迎来到 LLMWIC_Role_playing')) {
        contentDiv.innerHTML = '';
    }
    const userDiv = document.createElement('div');
    userDiv.className = 'user-message';
    userDiv.style.marginBottom = '16px';
    userDiv.style.textAlign = 'right';
    userDiv.innerHTML = `<span style="background:#3b82f6; color:white; padding:8px 16px; border-radius:20px; display:inline-block; max-width:80%;">${escapeHtml(text)}</span>`;
    contentDiv.appendChild(userDiv);
    scrollToBottom();
}

function appendSystemMessage(text) {
    let contentDiv = narrativeTextEl;
    const sysDiv = document.createElement('div');
    sysDiv.className = 'system-message';
    sysDiv.style.marginBottom = '8px';
    sysDiv.style.textAlign = 'center';
    sysDiv.style.fontSize = '0.8rem';
    sysDiv.style.color = '#6b7280';
    sysDiv.innerHTML = text;
    contentDiv.appendChild(sysDiv);
    scrollToBottom();
}

function showOptions(options) {
    const optionsArea = document.getElementById('optionsArea');
    const optionsList = document.getElementById('optionsList');
    optionsList.innerHTML = '';
    if (Array.isArray(options)) {
        options.forEach(opt => {
            const btn = document.createElement('button');
            btn.className = 'option-btn';
            btn.textContent = opt.text || opt;
            btn.onclick = () => {
                messageInput.value = opt.action || opt.text;
                sendAction();
                optionsArea.style.display = 'none';
            };
            optionsList.appendChild(btn);
        });
        optionsArea.style.display = 'block';
    }
}

function showLoadingIndicator() {
    const optionsArea = document.getElementById('optionsArea');
    if (optionsArea) optionsArea.style.display = 'none';
    let loadingDiv = document.getElementById('loadingIndicator');
    if (!loadingDiv) {
        loadingDiv = document.createElement('div');
        loadingDiv.id = 'loadingIndicator';
        loadingDiv.style.textAlign = 'center';
        loadingDiv.style.padding = '20px';
        loadingDiv.style.color = '#6b7280';
        narrativeTextEl.appendChild(loadingDiv);
    }
    loadingDiv.innerHTML = '<span class="loading"></span> AI 正在思考...';
    scrollToBottom();
}

function removeLoadingIndicator() {
    const loadingDiv = document.getElementById('loadingIndicator');
    if (loadingDiv) loadingDiv.remove();
}

function hideLoadingIndicator() {
    removeLoadingIndicator();
}

function scrollToBottom() {
    const narrativeArea = document.getElementById('narrativeArea');
    narrativeArea.scrollTop = narrativeArea.scrollHeight;
}

function stopGeneration() {
    if (abortController) {
        abortController.abort();
        abortController = null;
    }
}

async function regenerate() {
    if (!lastUserInput) {
        alert('没有可重新生成的消息');
        return;
    }
    if (isGenerating) stopGeneration();
    await new Promise(resolve => setTimeout(resolve, 500));

    const contentDiv = narrativeTextEl;
    const entries = contentDiv.querySelectorAll('.narrative-entry');
    if (entries.length > 0) entries[entries.length - 1].remove();

    await generateNarrative(lastUserInput, lastMode);
}

async function resetGame() {
    if (!confirm('确定要重置游戏吗？所有进度将丢失！')) return;
    try {
        await fetch(`/v1/game/reset/${currentSessionId}`, { method: 'POST' });
        currentSessionId = 'game_' + Date.now() + '_' + Math.random().toString(36).substr(2, 8);
        localStorage.setItem('gameSessionId', currentSessionId);

        narrativeTextEl.innerHTML = '欢迎来到 LLMWIC_Role_playing！<br><br>开始你的行动，你的冒险故事从此出发...';
        document.getElementById('optionsArea').style.display = 'none';

        appendSystemMessage('🎮 游戏已重置，开始新的冒险吧！');
        loadGameState();
    } catch (error) {
        console.error('重置失败', error);
        appendSystemMessage('重置失败: ' + error.message);
    }
}

// 存档/读档功能
function openSaveModal() {
    document.getElementById('saveName').value = `存档_${new Date().toLocaleString()}`;
    document.getElementById('saveModal').style.display = 'block';
}

function openLoadModal() {
    document.getElementById('loadModal').style.display = 'block';
    loadRecoveryPoints();
}

async function saveRecoveryPoint() {
    const name = document.getElementById('saveName').value;
    const desc = document.getElementById('saveDescription').value;

    if (!name) {
        alert('请输入存档名称');
        return;
    }

    try {
        const response = await fetch('/v1/game/recovery-point', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sessionId: currentSessionId, pointName: name, description: desc })
        });
        const result = await response.json();
        alert(result.message || '保存成功');
        document.getElementById('saveModal').style.display = 'none';
        document.getElementById('saveDescription').value = '';
    } catch (error) {
        console.error('保存失败', error);
        alert('保存失败: ' + error.message);
    }
}

async function loadRecoveryPoints() {
    const listEl = document.getElementById('recoveryList');
    listEl.innerHTML = '<p style="text-align:center; padding:20px;"><span class="loading"></span> 正在加载存档...</p>';

    try {
        const response = await fetch(`/v1/game/recovery-points/${currentSessionId}`);
        const data = await response.json();

        if (data.status === 'ok' && data.points && data.points.length > 0) {
            const ul = document.createElement('ul');
            data.points.forEach(p => {
                const li = document.createElement('li');
                if (p.isAuto) li.classList.add('auto-save');
                li.innerHTML = `
                    <strong>${escapeHtml(p.pointName)}</strong><br>
                    <small>📖 回合: ${p.turnNumber} | 📅 ${new Date(p.createdAt).toLocaleString()}</small>
                    ${p.description ? `<br><span style="color:#64748b;">📝 ${escapeHtml(p.description)}</span>` : ''}
                `;
                li.onclick = () => restoreToPoint(p.id);
                ul.appendChild(li);
            });
            listEl.innerHTML = '';
            listEl.appendChild(ul);
        } else {
            listEl.innerHTML = '<div class="empty-state">📭 暂无存档记录<br><small>点击"保存进度"创建第一个存档</small></div>';
        }
    } catch (error) {
        console.error('加载存档列表失败', error);
        listEl.innerHTML = '<div class="empty-state">❌ 加载失败，请重试</div>';
    }
}

async function restoreToPoint(id) {
    if (!confirm('⚠️ 确定要加载此存档吗？\n当前未保存的进度将会丢失！')) return;

    try {
        const response = await fetch(`/v1/game/restore/${id}`, { method: 'POST' });
        const result = await response.json();
        alert(result.message);
        if (result.status === 'ok') {
            document.getElementById('loadModal').style.display = 'none';
            location.reload();
        }
    } catch (error) {
        console.error('恢复失败', error);
        alert('恢复失败: ' + error.message);
    }
}

// ======================== 世界管理模块 ========================
let currentTraits = [];
let currentEditSessionId = null;
let worldManagerInitialized = false;

// 添加NPC行（支持完整描述）- 唯一的版本
function addNpcRow(containerId, values = {
    name: '',
    traits: [],
    description: '',
    personality: '',
    role: '',
    motivation: '',
    relationship: 0
}) {
    const container = document.getElementById(containerId);
    const div = document.createElement('div');
    div.className = 'npc-entry';
    div.style.cssText = 'border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; margin-bottom: 12px; background: #fafbfc;';

    div.innerHTML = `
        <div style="display: flex; gap: 8px; margin-bottom: 8px;">
            <input type="text" class="npc-name" value="${escapeHtml(values.name)}" placeholder="NPC名称" style="flex: 2;">
            <input type="text" class="npc-role" value="${escapeHtml(values.role)}" placeholder="身份/角色" style="flex: 1;">
            <button type="button" class="remove-npc" style="background: #fee2e2; border: none; border-radius: 4px; padding: 4px 8px;">🗑️</button>
        </div>
        <textarea class="npc-description" rows="2" placeholder="外貌描述、背景故事..." style="width: 100%; margin-bottom: 8px;">${escapeHtml(values.description)}</textarea>
        <textarea class="npc-personality" rows="2" placeholder="性格特点..." style="width: 100%; margin-bottom: 8px;">${escapeHtml(values.personality)}</textarea>
        <textarea class="npc-motivation" rows="2" placeholder="核心动机/目标..." style="width: 100%; margin-bottom: 8px;">${escapeHtml(values.motivation)}</textarea>
        <div style="display: flex; gap: 8px;">
            <select class="npc-trait" multiple size="3" style="flex: 2;"></select>
            <input type="number" class="npc-relationship" value="${values.relationship}" placeholder="好感度" style="width: 80px;">
        </div>
    `;

    fillTraitSelect(div.querySelector('.npc-trait'));
    if (values.traits && values.traits.length) {
        Array.from(div.querySelector('.npc-trait').options).forEach(opt => {
            if (values.traits.includes(opt.value)) opt.selected = true;
        });
    }

    div.querySelector('.remove-npc').onclick = () => div.remove();
    container.appendChild(div);
}

function fillTraitSelect(select) {
    select.innerHTML = '';
    currentTraits.forEach(t => {
        const opt = document.createElement('option');
        opt.value = t.traitName;
        opt.textContent = t.traitName;
        select.appendChild(opt);
    });
    select.size = Math.min(3, currentTraits.length);
}

async function loadTraits() {
    try {
        const response = await fetch('/v1/game/traits');
        if (response.ok) {
            currentTraits = await response.json();
        } else {
            currentTraits = [
                {id:1, traitName:"勇敢"}, {id:2, traitName:"狡猾"},
                {id:3, traitName:"善良"}, {id:4, traitName:"睿智"}
            ];
        }
    } catch (error) {
        console.error('加载特质失败，使用默认数据', error);
        currentTraits = [
            {id:1, traitName:"勇敢"}, {id:2, traitName:"狡猾"},
            {id:3, traitName:"善良"}, {id:4, traitName:"睿智"}
        ];
    }

    document.querySelectorAll('.npc-trait').forEach(select => fillTraitSelect(select));
}

// 加载世界列表（唯一的版本）
async function loadWorldList() {
    const select = document.getElementById('existingWorldSelect');
    if (!select) return;

    select.innerHTML = '<option value="">-- 请选择世界 --</option>';

    try {
        const response = await fetch('/v1/game/worlds');
        if (response.ok) {
            const worlds = await response.json();
            worlds.forEach(w => {
                const opt = document.createElement('option');
                opt.value = w.sessionId;
                opt.textContent = `${w.worldName || '未命名世界'} (回合: ${w.turnNumber})`;
                select.appendChild(opt);
            });
        }
    } catch (error) {
        console.error('加载世界列表失败:', error);
        select.innerHTML = '<option value="">-- 加载失败 --</option>';
    }
}

async function loadWorldPreview() {
    const sessionId = document.getElementById('existingWorldSelect').value;
    if (!sessionId) {
        document.getElementById('worldPreview').style.display = 'none';
        return;
    }

    try {
        const response = await fetch(`/v1/game/world/${sessionId}`);
        const data = await response.json();

        document.getElementById('previewBackground').innerHTML = escapeHtml(data.worldBackground || '无');
        document.getElementById('previewStoryHook').innerHTML = escapeHtml(data.storyHook || '无');
        document.getElementById('previewPlayer').innerHTML = `
            <strong>${escapeHtml(data.playerName)}</strong><br>
            ${data.playerDescription ? escapeHtml(data.playerDescription) : ''}<br>
            特质: ${data.playerTraits && data.playerTraits.length ? data.playerTraits.join('、') : '无'}
        `;

        let npcHtml = '';
        if (data.npcs && data.npcs.length) {
            data.npcs.forEach(npc => {
                npcHtml += `
                    <div style="margin-top: 8px; padding: 8px; background: white; border-radius: 6px;">
                        <strong>${escapeHtml(npc.name)}</strong> ${npc.role ? `(${escapeHtml(npc.role)})` : ''}<br>
                        ${npc.description ? escapeHtml(npc.description.substring(0, 100)) + '...' : ''}<br>
                        特质: ${npc.traits && npc.traits.length ? npc.traits.join('、') : '无'}
                    </div>
                `;
            });
        } else {
            npcHtml = '暂无NPC';
        }
        document.getElementById('previewNPCs').innerHTML = npcHtml;

        document.getElementById('worldPreview').style.display = 'block';
        window.selectedWorldSessionId = sessionId;

    } catch (err) {
        console.error('加载世界预览失败', err);
        document.getElementById('worldPreview').style.display = 'none';
    }
}

async function loadWorldToGame() {
    const sessionId = window.selectedWorldSessionId;
    if (!sessionId) {
        alert('请先选择一个世界');
        return;
    }

    if (confirm('确定要加载此世界吗？当前未保存的进度将会丢失！')) {
        localStorage.setItem('gameSessionId', sessionId);
        document.querySelector('.menu-item[data-page="role-playing"]').click();
        location.reload();
    }
}

async function createWorld() {
    const worldName = document.getElementById('worldName').value;
    const worldBackground = document.getElementById('worldBackground').value;
    const storyHook = document.getElementById('storyHook').value;
    const playerName = document.getElementById('playerName').value;
    const playerDescription = document.getElementById('playerDescription').value;

    const playerTraits = Array.from(document.getElementById('playerTraitsSelect').selectedOptions).map(opt => opt.value);

    const npcs = [];
    document.querySelectorAll('#npcList .npc-entry').forEach(entry => {
        const name = entry.querySelector('.npc-name').value.trim();
        if (name) {
            const role = entry.querySelector('.npc-role')?.value || '';
            const description = entry.querySelector('.npc-description')?.value || '';
            const personality = entry.querySelector('.npc-personality')?.value || '';
            const motivation = entry.querySelector('.npc-motivation')?.value || '';
            const traits = Array.from(entry.querySelector('.npc-trait').selectedOptions).map(opt => opt.value);
            const relationship = parseInt(entry.querySelector('.npc-relationship')?.value) || 0;
            npcs.push({ name, role, description, personality, motivation, traits, relationship });
        }
    });

    try {
        const response = await fetch('/v1/game/world', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                worldName, worldBackground, storyHook,
                playerName, playerDescription, playerTraits,
                npcs
            })
        });
        const result = await response.json();
        if (response.ok) {
            alert('世界创建成功！');
            loadWorldList();
            document.getElementById('createWorldForm').reset();
            document.getElementById('npcList').innerHTML = '';
        } else {
            alert('创建失败: ' + (result.message || '未知错误'));
        }
    } catch (error) {
        console.error('请求失败:', error);
        alert('网络错误，请检查后端服务');
    }
}

async function updateWorld() {
    if (!currentEditSessionId) {
        alert('没有选中要更新的世界');
        return;
    }

    const worldBackground = document.getElementById('editWorldBackground').value;
    const storyHook = document.getElementById('editStoryHook').value;
    const playerName = document.getElementById('editPlayerName').value;
    const playerDescription = document.getElementById('editPlayerDescription').value;
    const playerTraits = Array.from(document.getElementById('editPlayerTraits').selectedOptions).map(opt => opt.value);

    const npcs = [];
    document.querySelectorAll('#editNpcList .npc-entry').forEach(entry => {
        const name = entry.querySelector('.npc-name').value.trim();
        if (name) {
            const role = entry.querySelector('.npc-role')?.value || '';
            const description = entry.querySelector('.npc-description')?.value || '';
            const personality = entry.querySelector('.npc-personality')?.value || '';
            const motivation = entry.querySelector('.npc-motivation')?.value || '';
            const traits = Array.from(entry.querySelector('.npc-trait').selectedOptions).map(opt => opt.value);
            const relationship = parseInt(entry.querySelector('.npc-relationship')?.value) || 0;
            npcs.push({ name, role, description, personality, motivation, traits, relationship });
        }
    });

    try {
        const response = await fetch(`/v1/game/world/${currentEditSessionId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ worldBackground, storyHook, playerName, playerDescription, playerTraits, npcs })
        });
        const result = await response.json();
        if (response.ok) {
            alert('更新成功！');
            document.getElementById('editWorldModal').style.display = 'none';
        } else {
            alert('更新失败: ' + (result.message || '未知错误'));
        }
    } catch (error) {
        console.error('更新请求失败:', error);
        alert('网络错误');
    }
}

function openEditWorldModal() {
    const sessionId = window.selectedWorldSessionId;
    if (!sessionId) {
        alert('请先选择一个世界');
        return;
    }

    loadWorldDetail();
    document.getElementById('editWorldModal').style.display = 'block';
}

async function loadWorldDetail() {
    const sessionId = window.selectedWorldSessionId;
    if (!sessionId) return;

    try {
        const response = await fetch(`/v1/game/world/${sessionId}`);
        if (response.ok) {
            const data = await response.json();
            currentEditSessionId = sessionId;
            document.getElementById('editWorldBackground').value = data.worldBackground || '';
            document.getElementById('editStoryHook').value = data.storyHook || '';
            document.getElementById('editPlayerName').value = data.playerName || '冒险者';
            document.getElementById('editPlayerDescription').value = data.playerDescription || '';

            // 填充玩家特质
            const playerTraitsSelect = document.getElementById('editPlayerTraits');
            playerTraitsSelect.innerHTML = '';
            currentTraits.forEach(t => {
                const opt = document.createElement('option');
                opt.value = t.traitName;
                opt.textContent = t.traitName;
                if (data.playerTraits && data.playerTraits.includes(t.traitName)) opt.selected = true;
                playerTraitsSelect.appendChild(opt);
            });

            const editNpcContainer = document.getElementById('editNpcList');
            editNpcContainer.innerHTML = '';
            if (data.npcs && data.npcs.length) {
                data.npcs.forEach(npc => addNpcRow('editNpcList', npc));
            } else {
                addNpcRow('editNpcList', {});
            }
        }
    } catch (error) {
        console.error('加载详情失败:', error);
    }
}

function loadWorldManager() {
    if (!worldManagerInitialized) {
        worldManagerInitialized = true;

        const createForm = document.getElementById('createWorldForm');
        if (createForm) {
            createForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                await createWorld();
            });
        }

        const addNpcBtn = document.getElementById('addNpcBtn');
        if (addNpcBtn) {
            addNpcBtn.onclick = () => addNpcRow('npcList', {});
        }

        const refreshBtn = document.getElementById('refreshWorldListBtn');
        if (refreshBtn) {
            refreshBtn.onclick = loadWorldList;
        }

        const worldSelect = document.getElementById('existingWorldSelect');
        if (worldSelect) {
            worldSelect.onchange = loadWorldPreview;
        }

        const loadWorldBtn = document.getElementById('loadWorldBtn');
        if (loadWorldBtn) {
            loadWorldBtn.onclick = loadWorldToGame;
        }

        const editWorldBtn = document.getElementById('editWorldBtn');
        if (editWorldBtn) {
            editWorldBtn.onclick = openEditWorldModal;
        }

        const saveChangesBtn = document.getElementById('saveWorldChangesBtn');
        if (saveChangesBtn) {
            saveChangesBtn.onclick = updateWorld;
        }

        const editAddNpcBtn = document.getElementById('editAddNpcBtn');
        if (editAddNpcBtn) {
            editAddNpcBtn.onclick = () => addNpcRow('editNpcList', {});
        }

        const closeEditModal = document.querySelector('.close-edit');
        if (closeEditModal) {
            closeEditModal.onclick = () => {
                document.getElementById('editWorldModal').style.display = 'none';
            };
        }
    }

    loadTraits();
    loadWorldList();

    const worldPreview = document.getElementById('worldPreview');
    if (worldPreview) worldPreview.style.display = 'none';
    const existingWorldSelect = document.getElementById('existingWorldSelect');
    if (existingWorldSelect) existingWorldSelect.value = '';
}

// ======================== 加载世界功能（从快照表加载） ========================

function openLoadWorldModal() {
    let modal = document.getElementById('loadWorldModal');
    if (!modal) {
        createLoadWorldModal();
        modal = document.getElementById('loadWorldModal');
    }

    loadWorldSnapshotList();
    modal.style.display = 'block';
}

function createLoadWorldModal() {
    const modalHTML = `
        <div id="loadWorldModal" class="modal">
            <div class="modal-content" style="max-width: 800px; max-height: 80vh; overflow-y: auto;">
                <span class="close-world-modal">&times;</span>
                <h3>📀 加载世界存档</h3>
                <p style="color: #64748b; margin-bottom: 16px;">从已保存的世界中选择一个加载，所有未保存的进度将会丢失。</p>
                
                <div class="form-group">
                    <label>选择世界/存档</label>
                    <select id="worldSnapshotSelect" style="width: 100%; padding: 8px;">
                        <option value="">-- 请选择 --</option>
                    </select>
                </div>
                
                <div id="worldSnapshotPreview" style="display: none; margin-top: 16px;">
                    <div style="background: #f8fafc; padding: 12px; border-radius: 8px; margin-bottom: 12px;">
                        <strong>🌍 世界名称:</strong> 
                        <span id="previewWorldName" style="color: #1e293b;"></span>
                    </div>
                    <div style="background: #f8fafc; padding: 12px; border-radius: 8px; margin-bottom: 12px;">
                        <strong>📖 回合数:</strong> 
                        <span id="previewTurnNumber" style="color: #1e293b;"></span>
                    </div>
                    <div style="background: #f8fafc; padding: 12px; border-radius: 8px; margin-bottom: 12px;">
                        <strong>📅 最后更新:</strong> 
                        <span id="previewLastUpdate" style="color: #1e293b;"></span>
                    </div>
                    <div style="background: #f8fafc; padding: 12px; border-radius: 8px; margin-bottom: 12px;">
                        <strong>📖 世界背景:</strong>
                        <div id="previewWorldBackground" style="margin-top: 8px; white-space: pre-wrap; max-height: 150px; overflow-y: auto; color: #475569;"></div>
                    </div>
                    <div style="background: #f8fafc; padding: 12px; border-radius: 8px; margin-bottom: 12px;">
                        <strong>🎭 故事引子:</strong>
                        <div id="previewWorldStoryHook" style="margin-top: 8px; white-space: pre-wrap; max-height: 150px; overflow-y: auto; color: #475569;"></div>
                    </div>
                    <div style="background: #f8fafc; padding: 12px; border-radius: 8px; margin-bottom: 12px;">
                        <strong>👤 玩家角色:</strong>
                        <div id="previewWorldPlayer" style="margin-top: 8px; color: #475569;"></div>
                    </div>
                    <div style="background: #f8fafc; padding: 12px; border-radius: 8px; margin-bottom: 12px;">
                        <strong>👥 核心NPC:</strong>
                        <div id="previewWorldNPCs" style="margin-top: 8px; color: #475569;"></div>
                    </div>
                    <button id="confirmLoadWorldBtn" class="btn" style="width: 100%; margin-top: 16px;">✅ 确认加载此世界</button>
                </div>
            </div>
        </div>
    `;

    document.body.insertAdjacentHTML('beforeend', modalHTML);

    const closeBtn = document.querySelector('.close-world-modal');
    if (closeBtn) {
        closeBtn.onclick = () => {
            document.getElementById('loadWorldModal').style.display = 'none';
        };
    }

    const select = document.getElementById('worldSnapshotSelect');
    if (select) {
        select.onchange = previewWorldSnapshot;
    }

    const confirmBtn = document.getElementById('confirmLoadWorldBtn');
    if (confirmBtn) {
        confirmBtn.onclick = confirmLoadWorld;
    }
}

async function loadWorldSnapshotList() {
    const select = document.getElementById('worldSnapshotSelect');
    if (!select) return;

    select.innerHTML = '<option value="">-- 加载中... --</option>';

    try {
        const response = await fetch('/v1/game/worlds');
        if (response.ok) {
            const worlds = await response.json();

            select.innerHTML = '<option value="">-- 请选择世界 --</option>';

            if (worlds && worlds.length > 0) {
                const grouped = {};
                worlds.forEach(w => {
                    if (!grouped[w.sessionId]) {
                        grouped[w.sessionId] = [];
                    }
                    grouped[w.sessionId].push(w);
                });

                for (const [sessionId, snapshots] of Object.entries(grouped)) {
                    const optgroup = document.createElement('optgroup');
                    optgroup.label = `会话: ${sessionId.substring(0, 30)}...`;

                    snapshots.forEach(snapshot => {
                        const option = document.createElement('option');
                        option.value = snapshot.sessionId;
                        option.textContent = `${snapshot.worldName || '未命名世界'} (回合: ${snapshot.turnNumber}) - ${new Date(snapshot.lastUpdate).toLocaleString()}`;
                        option.setAttribute('data-snapshot-id', snapshot.snapshotId);
                        optgroup.appendChild(option);
                    });

                    select.appendChild(optgroup);
                }
            } else {
                select.innerHTML = '<option value="">-- 暂无可用世界，请先创建 --</option>';
            }
        } else {
            select.innerHTML = '<option value="">-- 加载失败，请重试 --</option>';
        }
    } catch (error) {
        console.error('加载世界列表失败:', error);
        select.innerHTML = '<option value="">-- 加载失败，请检查后端服务 --</option>';
    }
}

async function previewWorldSnapshot() {
    const select = document.getElementById('worldSnapshotSelect');
    const sessionId = select.value;
    const selectedOption = select.options[select.selectedIndex];
    const snapshotId = selectedOption ? selectedOption.getAttribute('data-snapshot-id') : null;

    if (!sessionId || !snapshotId) {
        document.getElementById('worldSnapshotPreview').style.display = 'none';
        return;
    }

    try {
        const response = await fetch(`/v1/game/world/${sessionId}`);
        if (response.ok) {
            const data = await response.json();

            document.getElementById('previewWorldName').innerHTML = escapeHtml(data.worldName || '未命名');
            document.getElementById('previewTurnNumber').innerHTML = data.turnNumber || 0;
            document.getElementById('previewLastUpdate').innerHTML = new Date().toLocaleString();
            document.getElementById('previewWorldBackground').innerHTML = escapeHtml(data.worldBackground || '无');
            document.getElementById('previewWorldStoryHook').innerHTML = escapeHtml(data.storyHook || '无');

            let playerHtml = `<strong>${escapeHtml(data.playerName)}</strong>`;
            if (data.playerDescription) {
                playerHtml += `<br>📝 ${escapeHtml(data.playerDescription)}`;
            }
            if (data.playerTraits && data.playerTraits.length) {
                playerHtml += `<br>🏷️ 特质: ${data.playerTraits.map(t => `【${t}】`).join(' ')}`;
            }
            document.getElementById('previewWorldPlayer').innerHTML = playerHtml;

            let npcHtml = '';
            if (data.npcs && data.npcs.length) {
                data.npcs.forEach(npc => {
                    npcHtml += `
                        <div style="margin-top: 12px; padding: 8px; background: white; border-radius: 8px; border-left: 3px solid #3b82f6;">
                            <strong>${escapeHtml(npc.name)}</strong> ${npc.role ? `(${escapeHtml(npc.role)})` : ''}<br>
                            ${npc.description ? `<span style="font-size: 0.85rem;">📖 ${escapeHtml(npc.description.substring(0, 100))}${npc.description.length > 100 ? '...' : ''}</span><br>` : ''}
                            ${npc.personality ? `<span style="font-size: 0.85rem;">💭 ${escapeHtml(npc.personality)}</span><br>` : ''}
                            ${npc.traits && npc.traits.length ? `<span style="font-size: 0.85rem;">🏷️ 特质: ${npc.traits.map(t => `【${t}】`).join(' ')}</span>` : ''}
                            ${npc.relationship ? `<span style="font-size: 0.85rem;">💝 好感度: ${npc.relationship}</span>` : ''}
                        </div>
                    `;
                });
            } else {
                npcHtml = '<span style="color: #94a3b8;">暂无NPC</span>';
            }
            document.getElementById('previewWorldNPCs').innerHTML = npcHtml;

            window.selectedWorldSnapshot = {
                sessionId: sessionId,
                snapshotId: snapshotId
            };

            document.getElementById('worldSnapshotPreview').style.display = 'block';
        } else {
            document.getElementById('worldSnapshotPreview').style.display = 'none';
        }
    } catch (error) {
        console.error('加载世界预览失败:', error);
        document.getElementById('worldSnapshotPreview').style.display = 'none';
    }
}

async function confirmLoadWorld() {
    if (!window.selectedWorldSnapshot) {
        alert('请先选择一个世界');
        return;
    }

    if (!confirm('⚠️ 确定要加载此世界吗？\n当前未保存的进度将会丢失！\n加载后将开始新的冒险。')) {
        return;
    }

    try {
        localStorage.setItem('gameSessionId', window.selectedWorldSnapshot.sessionId);

        const modal = document.getElementById('loadWorldModal');
        if (modal) {
            modal.style.display = 'none';
        }

        const gameMenuItem = document.querySelector('.menu-item[data-page="role-playing"]');
        if (gameMenuItem) {
            gameMenuItem.click();
        }

        appendSystemMessage('🎮 正在加载世界，请稍候...');

        setTimeout(() => {
            location.reload();
        }, 500);

    } catch (error) {
        console.error('加载世界失败:', error);
        alert('加载失败: ' + error.message);
    }
}

// ========== 侧边栏菜单导航 ==========
function initSidebarMenu() {
    const menuItems = document.querySelectorAll('.menu-item');
    menuItems.forEach(item => {
        item.addEventListener('click', () => {
            const action = item.getAttribute('data-action');
            if (action === 'redirect') {
                const url = item.getAttribute('data-url');
                if (url) window.open(url, '_blank');
            } else {
                menuItems.forEach(i => i.classList.remove('active'));
                item.classList.add('active');
                const pageId = item.getAttribute('data-page');
                if (pageId) {
                    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
                    const targetPage = document.getElementById(`${pageId}-page`);
                    if (targetPage) {
                        targetPage.classList.add('active');
                        if (pageId === 'world-manager') {
                            loadWorldManager();
                        }
                    }
                }
            }
        });
    });
}

// ========== 工具函数 ==========
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}