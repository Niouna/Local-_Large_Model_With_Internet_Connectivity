// API 基础路径
const API_BASE = '/admin';

// 当前分页状态
let currentPage = 0;
let pageSize = 10;
let totalPages = 1;

// 筛选条件
let filters = {
    sessionId: '',
    level: '',
    isActive: ''
};

// RAG 日志分页状态
let ragCurrentPage = 0;
let ragTotalPages = 1;
let ragFilterSession = '';

// 初始化
document.addEventListener('DOMContentLoaded', () => {
    // 1. 主标签页切换
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            const tabId = btn.dataset.tab;
            document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
            document.getElementById(tabId).classList.add('active');

            if (tabId === 'dashboard') loadDashboard();
            if (tabId === 'sessions') loadSessions();
            if (tabId === 'memories') loadMemories(currentPage);
            if (tabId === 'rag-logs') loadRagLogs(ragCurrentPage);
            if (tabId === 'dashboard') loadDashboard();
            if (tabId === 'sessions') loadSessions();
            if (tabId === 'memories') loadMemories(currentPage);
            if (tabId === 'rag-logs') loadRagLogs(ragCurrentPage);
            if (tabId === 'traits') loadTraits(0);
            if (tabId === 'game-events') loadGameEvents(0);
            if (tabId === 'game-memories') loadGameMemories(0);
            if (tabId === 'game-recovery') loadGameRecoveryPoints(0);
            if (tabId === 'game-snapshots') loadGameSnapshots(0);
        });
    });

    // 叙事记忆
    document.getElementById('refresh-game-memories')?.addEventListener('click', () => loadGameMemories(0));
    document.getElementById('apply-game-memories-filter')?.addEventListener('click', () => {
        gameMemoriesFilterSession = document.getElementById('filter-game-memories-session').value;
        gameMemoriesFilterType = document.getElementById('filter-game-memories-type').value;
        loadGameMemories(0);
    });

    // 恢复点
    document.getElementById('refresh-game-recovery')?.addEventListener('click', () => loadGameRecoveryPoints(0));
    document.getElementById('apply-game-recovery-filter')?.addEventListener('click', () => {
        gameRecoveryFilterSession = document.getElementById('filter-game-recovery-session').value;
        loadGameRecoveryPoints(0);
    });

    // 快照管理
    document.getElementById('refresh-game-snapshots')?.addEventListener('click', () => loadGameSnapshots(0));
    document.getElementById('apply-game-snapshots-filter')?.addEventListener('click', () => {
        gameSnapshotsFilterSession = document.getElementById('filter-game-snapshots-session').value;
        loadGameSnapshots(0);
    });

    // 游戏事件
    document.getElementById('refresh-game-events')?.addEventListener('click', () => loadGameEvents(0));
    document.getElementById('apply-game-events-filter')?.addEventListener('click', () => {
        gameEventsFilterSession = document.getElementById('filter-game-events-session').value;
        loadGameEvents(0);
    });

// 叙事记忆
    document.getElementById('refresh-game-memories')?.addEventListener('click', () => loadGameMemories(0));
    document.getElementById('apply-game-memories-filter')?.addEventListener('click', () => {
        gameMemoriesFilterSession = document.getElementById('filter-game-memories-session').value;
        gameMemoriesFilterType = document.getElementById('filter-game-memories-type').value;
        loadGameMemories(0);
    });

    // 特质管理相关
    document.getElementById('refresh-traits')?.addEventListener('click', () => loadTraits());
    document.getElementById('add-trait')?.addEventListener('click', () => openTraitModal());
    document.getElementById('cancel-trait')?.addEventListener('click', () => closeTraitModal());
    document.getElementById('trait-form')?.addEventListener('submit', saveTrait);
    document.querySelector('.close-trait')?.addEventListener('click', closeTraitModal);

    // 2. 仪表盘内部状态切换
    document.querySelectorAll('.status-tab-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.status-tab-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            const statusId = btn.dataset.status;
            document.querySelectorAll('.status-panel').forEach(p => p.classList.remove('active'));
            document.getElementById(statusId).classList.add('active');
        });
    });

    // 加载初始仪表盘
    loadDashboard();

    // 会话管理按钮
    document.getElementById('refresh-sessions').addEventListener('click', loadSessions);

    // 记忆管理事件
    document.getElementById('refresh-memories').addEventListener('click', () => loadMemories(0));
    document.getElementById('apply-filter').addEventListener('click', () => {
        filters.sessionId = document.getElementById('filter-session').value;
        filters.level = document.getElementById('filter-level').value;
        filters.isActive = document.getElementById('filter-active').value;
        loadMemories(0);
    });
    document.getElementById('clear-all-memories').addEventListener('click', clearAllMemories);

    // RAG 追踪事件
    document.getElementById('refresh-rag-logs').addEventListener('click', () => loadRagLogs(0));
    document.getElementById('apply-rag-filter').addEventListener('click', () => {
        ragFilterSession = document.getElementById('rag-filter-session').value;
        loadRagLogs(0);
    });

    // 弹窗关闭（会话详情）
    document.querySelector('.close').addEventListener('click', () => {
        document.getElementById('session-detail-modal').style.display = 'none';
    });
    window.addEventListener('click', (e) => {
        if (e.target.classList.contains('modal')) {
            e.target.style.display = 'none';
        }
    });

    // RAG 详情弹窗关闭
    document.querySelector('.close-rag')?.addEventListener('click', () => {
        document.getElementById('rag-detail-modal').style.display = 'none';
    });
});

// ---------- 仪表盘（不变）----------
async function loadDashboard() {
    try {
        const [ollamaRes, sessionRes, memoryRes, snapshotRes] = await Promise.all([
            fetch(`${API_BASE}/status/ollama`),
            fetch(`${API_BASE}/status/sessions`),
            fetch(`${API_BASE}/status/memories`),
            fetch(`${API_BASE}/status/snapshot-last`)
        ]);

        const ollama = await ollamaRes.json();
        const session = await sessionRes.json();
        const memory = await memoryRes.json();
        const snapshot = await snapshotRes.json();

        document.getElementById('ollama-status').innerHTML = `
            <div style="display:flex; gap:15px; flex-wrap:wrap;">
                <div style="background:#ecfdf5; padding:10px 15px; border-radius:8px; border:1px solid #a7f3d0; flex:1;">
                    <strong>GPU (11434):</strong> ${ollama.gpu.running ? '<span style="color:#059669">✅ 运行中</span>' : '<span style="color:#dc2626">❌ 停止</span>'}
                    <div style="margin-top:5px; font-size:0.85rem; color:#4b5563;">模型: ${ollama.gpu.models || '无'}</div>
                </div>
                <div style="background:#eff6ff; padding:10px 15px; border-radius:8px; border:1px solid #bfdbfe; flex:1;">
                    <strong>CPU (11435):</strong> ${ollama.cpu.running ? '<span style="color:#059669">✅ 运行中</span>' : '<span style="color:#dc2626">❌ 停止</span>'}
                    <div style="margin-top:5px; font-size:0.85rem; color:#4b5563;">模型: ${ollama.cpu.models || '无'}</div>
                </div>
            </div>
        `;

        document.getElementById('session-stats').innerHTML = `
            <div style="display:grid; grid-template-columns: 1fr 1fr; gap:15px;">
                <div style="background:#f0f9ff; padding:15px; border-radius:8px; text-align:center; border:1px solid #bae6fd;">
                    <div style="font-size:0.9rem; color:#64748b;">活跃会话</div>
                    <div style="font-size:1.5rem; font-weight:bold; color:#0284c7;">${session.activeCount}</div>
                </div>
                <div style="background:#fef2f2; padding:15px; border-radius:8px; text-align:center; border:1px solid #fecaca;">
                    <div style="font-size:0.9rem; color:#64748b;">总对话轮数</div>
                    <div style="font-size:1.5rem; font-weight:bold; color:#dc2626;">${session.totalTurns}</div>
                </div>
            </div>
        `;

        document.getElementById('memory-stats').innerHTML = `
            <div style="background:#f5f3ff; padding:20px; border-radius:8px; text-align:center; border:1px solid #ddd6fe;">
                <div style="font-size:0.9rem; color:#64748b;">记忆总数</div>
                <div style="font-size:1.8rem; font-weight:bold; color:#7c3aed; margin-top:5px;">${memory.total}</div>
            </div>
        `;

        document.getElementById('snapshot-info').innerHTML = `
            <div style="background:#fffbeb; padding:15px; border-radius:8px; border:1px solid #fde68a; display:flex; align-items:center; justify-content:space-between;">
                <span style="color:#92400e; font-weight:500;">最后快照时间:</span>
                <span style="color:#78350f; font-family:monospace;">${snapshot.lastSnapshotTime ? new Date(snapshot.lastSnapshotTime).toLocaleString() : '未生成'}</span>
            </div>
        `;
    } catch (err) {
        console.error('仪表盘加载失败', err);
    }
}

// ---------- 会话管理（不变）----------
async function loadSessions() {
    try {
        const res = await fetch(`${API_BASE}/sessions`);
        const sessions = await res.json();
        const tbody = document.getElementById('sessions-body');
        if (sessions.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; color:#9ca3af;">暂无活跃会话</td></tr>';
            return;
        }
        tbody.innerHTML = sessions.map(s => `
            <tr>
                <td style="font-family:monospace; font-size:0.85rem;">${s.sessionId}</td>
                <td>${s.model}</td>
                <td>${s.turnCount}</td>
                <td>${s.lastActivityTime ? new Date(s.lastActivityTime).toLocaleString() : '-'}</td>
                <td>
                    <button class="btn" onclick="viewSessionDetail('${s.sessionId}')">查看</button>
                    <button class="btn danger" onclick="endSession('${s.sessionId}')">结束</button>
                </td>
            </tr>
        `).join('');
    } catch (err) {
        console.error('加载会话失败', err);
    }
}

window.viewSessionDetail = async (sessionId) => {
    try {
        const res = await fetch(`${API_BASE}/sessions/${sessionId}`);
        const data = await res.json();
        document.getElementById('detail-session-id').innerText = `会话ID: ${data.sessionId}`;

        const historyHtml = data.history.map(h => `
            <div style="margin-bottom:12px; padding:12px; background:${h.role === 'user' ? '#eff6ff' : '#f0fdf4'}; border-radius:8px; border-left: 4px solid ${h.role === 'user' ? '#3b82f6' : '#10b981'};">
                <div style="font-size:0.8rem; color:#64748b; margin-bottom:4px;">
                    <strong>${h.role === 'user' ? '👤 用户' : '🤖 AI'}</strong> 
                    <span style="float:right">${new Date(h.timestamp).toLocaleTimeString()}</span>
                </div>
                <div style="white-space:pre-wrap; color:#1f2937;">${escapeHtml(h.content)}</div>
            </div>
        `).join('');

        document.getElementById('detail-history').innerHTML = `<h4 style="margin-bottom:10px; color:#374151;">对话历史</h4>${historyHtml || '<div style="color:#9ca3af">暂无记录</div>'}`;
        document.getElementById('detail-memory').innerHTML = `<h4 style="margin-bottom:10px; color:#374151; margin-top:20px;">关联记忆上下文</h4><div style="white-space:pre-wrap; background:#f9fafb; padding:10px; border-radius:6px; border:1px solid #e5e7eb;">${escapeHtml(data.memoryContext || '无')}</div>`;

        document.getElementById('session-detail-modal').style.display = 'block';
    } catch (err) {
        console.error('加载会话详情失败', err);
    }
};

window.endSession = async (sessionId) => {
    if (!confirm(`确定要结束会话 ${sessionId} 吗？`)) return;
    try {
        await fetch(`${API_BASE}/sessions/${sessionId}`, { method: 'DELETE' });
        loadSessions();
    } catch (err) {
        console.error('结束会话失败', err);
    }
};

// ---------- 记忆管理（不变）----------
async function loadMemories(page = 0) {
    currentPage = page;
    const params = new URLSearchParams({
        page,
        size: pageSize,
        sessionId: filters.sessionId || '',
        level: filters.level || '',
        isActive: filters.isActive || ''
    });
    try {
        const res = await fetch(`${API_BASE}/memories?${params}`);
        const data = await res.json();
        const memories = data.content || [];
        totalPages = data.totalPages || 1;

        const tbody = document.getElementById('memories-body');
        if (memories.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; color:#9ca3af;">暂无记忆数据</td></tr>';
        } else {
            tbody.innerHTML = memories.map(m => `
                <tr>
                    <td style="font-family:monospace; font-size:0.85rem;">${m.id}</td>
                    <td style="font-family:monospace; font-size:0.85rem;">${m.sessionId}</td>
                    <td><span style="background:#e0e7ff; color:#4338ca; padding:2px 6px; border-radius:4px; font-size:0.8rem;">L${m.level}</span></td>
                    <td style="max-width:300px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;" title="${escapeHtml(m.content)}">${escapeHtml(m.content.substring(0, 60))}${m.content.length > 60 ? '...' : ''}</td>
                    <td>${new Date(m.createdAt).toLocaleDateString()}</td>
                    <td>${m.isActive ? '<span style="color:#10b981">✅</span>' : '<span style="color:#9ca3af">❌</span>'}</td>
                    <td>
                        <button class="btn danger" style="padding:4px 8px; font-size:0.8rem;" onclick="deleteMemory(${m.id})">删除</button>
                    </td>
                </tr>
            `).join('');
        }
        renderPagination();
    } catch (err) {
        console.error('加载记忆失败', err);
    }
}

function renderPagination() {
    const container = document.getElementById('memories-pagination');
    if (totalPages <= 1) {
        container.innerHTML = '';
        return;
    }
    let html = '';
    for (let i = 0; i < totalPages; i++) {
        html += `<button class="page-btn ${i === currentPage ? 'active' : ''}" onclick="loadMemories(${i})">${i+1}</button>`;
    }
    container.innerHTML = html;
}

window.deleteMemory = async (id) => {
    if (!confirm(`确定要删除记忆 ID ${id} 吗？`)) return;
    try {
        await fetch(`${API_BASE}/memories/${id}`, { method: 'DELETE' });
        loadMemories(currentPage);
    } catch (err) {
        console.error('删除记忆失败', err);
    }
};

async function clearAllMemories() {
    if (!confirm('⚠️ 严重警告：确定要清空所有记忆吗？此操作不可恢复！')) return;
    try {
        await fetch(`${API_BASE}/memories/all?confirm=true`, { method: 'DELETE' });
        loadMemories(0);
    } catch (err) {
        console.error('清空记忆失败', err);
    }
}

// ---------- 新增：RAG 追踪 ----------
async function loadRagLogs(page = 0) {
    console.log('loadRagLogs 被调用, page =', page); // 添加日志
    ragCurrentPage = page;
    const params = new URLSearchParams({
        page,
        size: 10,
        sessionId: ragFilterSession || ''
    });
    try {
        const url = `${API_BASE}/rag-logs?${params}`;
        console.log('请求 URL:', url); // 添加日志
        const res = await fetch(url);
        if (!res.ok) {
            throw new Error(`HTTP ${res.status}`);
        }
        const data = await res.json();
        console.log('返回数据:', data); // 添加日志
        const logs = data.content || [];
        ragTotalPages = data.totalPages || 1;

        const tbody = document.getElementById('rag-logs-body');
        if (!tbody) {
            console.error('找不到 rag-logs-body 元素');
            return;
        }

        if (logs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:#9ca3af;">暂无RAG请求记录</td></tr>';
        } else {
            tbody.innerHTML = logs.map(log => `
                <tr>
                    <td style="white-space:nowrap;">${new Date(log.requestTime).toLocaleString()}</td>
                    <td style="font-family:monospace; font-size:0.85rem;">${escapeHtml(log.sessionId || '-')}</td>
                    <td style="max-width:200px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;" title="${escapeHtml(log.userMessage)}">${escapeHtml(log.userMessage.substring(0, 50))}${log.userMessage.length > 50 ? '...' : ''}</td>
                    <td>${log.needWebSearch ? '<span style="color:#10b981">✅ 是</span>' : '<span style="color:#9ca3af">❌ 否</span>'}</td>
                    <td>${log.processTimeMs || '-'}</td>
                    <td><button class="btn" style="padding:4px 12px;" onclick="viewRagDetail(${log.id})">详情</button></td>
                </tr>
            `).join('');
        }
        renderRagPagination();
    } catch (err) {
        console.error('加载RAG日志失败', err);
        const tbody = document.getElementById('rag-logs-body');
        if (tbody) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:#ef4444;">加载失败: ' + err.message + '</td></tr>';
        }
    }
}

function renderRagPagination() {
    const container = document.getElementById('rag-logs-pagination');
    if (ragTotalPages <= 1) {
        container.innerHTML = '';
        return;
    }
    let html = '';
    for (let i = 0; i < ragTotalPages; i++) {
        html += `<button class="page-btn ${i === ragCurrentPage ? 'active' : ''}" onclick="loadRagLogs(${i})">${i+1}</button>`;
    }
    container.innerHTML = html;
}

// 查看RAG日志详情
window.viewRagDetail = async (id) => {
    try {
        const res = await fetch(`${API_BASE}/rag-logs/${id}`);
        const log = await res.json();
        const content = `
            <div style="margin-bottom:20px; padding:12px; background:#f9fafb; border-radius:8px;">
                <strong>请求时间:</strong> ${new Date(log.requestTime).toLocaleString()}
            </div>
            <div style="margin-bottom:20px; padding:12px; background:#f9fafb; border-radius:8px;">
                <strong>会话ID:</strong> ${log.sessionId || '-'}
            </div>
            <div style="margin-bottom:20px; padding:12px; background:#f9fafb; border-radius:8px;">
                <strong>用户消息:</strong>
                <div style="margin-top:8px; white-space:pre-wrap; background:white; padding:10px; border:1px solid #e5e7eb; border-radius:6px;">${escapeHtml(log.userMessage)}</div>
            </div>
            <div style="margin-bottom:20px; padding:12px; background:#f9fafb; border-radius:8px;">
                <strong>联网搜索:</strong> ${log.needWebSearch ? '是' : '否'}
            </div>
            ${log.realQuery ? `
            <div style="margin-bottom:20px; padding:12px; background:#f9fafb; border-radius:8px;">
                <strong>真实查询词:</strong>
                <div style="margin-top:8px; white-space:pre-wrap; background:white; padding:10px; border:1px solid #e5e7eb; border-radius:6px;">${escapeHtml(log.realQuery)}</div>
            </div>
            ` : ''}
            ${log.searchResult ? `
            <div style="margin-bottom:20px; padding:12px; background:#f9fafb; border-radius:8px;">
                <strong>搜索结果:</strong>
                <div style="margin-top:8px; white-space:pre-wrap; background:white; padding:10px; border:1px solid #e5e7eb; border-radius:6px; max-height:200px; overflow:auto;">${escapeHtml(log.searchResult)}</div>
            </div>
            ` : ''}
            ${log.memoryContext ? `
            <div style="margin-bottom:20px; padding:12px; background:#f9fafb; border-radius:8px;">
                <strong>记忆上下文:</strong>
                <div style="margin-top:8px; white-space:pre-wrap; background:white; padding:10px; border:1px solid #e5e7eb; border-radius:6px; max-height:200px; overflow:auto;">${escapeHtml(log.memoryContext)}</div>
            </div>
            ` : ''}
            <div style="margin-bottom:20px; padding:12px; background:#f9fafb; border-radius:8px;">
                <strong>最终 Prompt:</strong>
                <div style="margin-top:8px; white-space:pre-wrap; background:white; padding:10px; border:1px solid #e5e7eb; border-radius:6px; max-height:300px; overflow:auto;">${escapeHtml(log.finalPrompt)}</div>
            </div>
            <div style="margin-bottom:20px; padding:12px; background:#f9fafb; border-radius:8px;">
                <strong>AI 回复:</strong>
                <div style="margin-top:8px; white-space:pre-wrap; background:white; padding:10px; border:1px solid #e5e7eb; border-radius:6px; max-height:300px; overflow:auto;">${escapeHtml(log.aiResponse)}</div>
            </div>
            ${log.processTimeMs ? `
            <div style="margin-bottom:20px; padding:12px; background:#f9fafb; border-radius:8px;">
                <strong>处理耗时:</strong> ${log.processTimeMs} ms
            </div>
            ` : ''}
        `;
        document.getElementById('rag-detail-content').innerHTML = content;
        document.getElementById('rag-detail-modal').style.display = 'block';
    } catch (err) {
        console.error('加载RAG日志详情失败', err);
    }
};

// 辅助函数：转义HTML
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ---------- 特质管理 ----------
let traitCurrentPage = 0;
let traitTotalPages = 1;
let traitPageSize = 10;

async function loadTraits(page = 0) {
    traitCurrentPage = page;
    const params = new URLSearchParams({
        page,
        size: traitPageSize,
        sort: 'traitName,asc'
    });
    try {
        const res = await fetch(`/v1/game/traits?${params}`);
        const data = await res.json();
        // 注意：原有的 /v1/game/traits 返回的是列表，没有分页，我们可能需要调整。
        // 为了分页，我们使用 JPA 的分页查询。但之前没有分页接口，这里我们暂时假设返回全量列表，前端自己做分页。
        // 为了简单，我们直接使用全量列表，并在前端实现前端分页（或改为后端分页）。
        // 由于时间关系，这里使用前端分页简单处理。
        const traits = Array.isArray(data) ? data : data.content || [];
        traitTotalPages = Math.ceil(traits.length / traitPageSize);
        const start = traitCurrentPage * traitPageSize;
        const pageTraits = traits.slice(start, start + traitPageSize);

        const tbody = document.getElementById('traits-body');
        if (pageTraits.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;">暂无特质数据</td></tr>';
        } else {
            tbody.innerHTML = pageTraits.map(t => `
                <tr>
                    <td>${t.id}</td>
                    <td><strong>${escapeHtml(t.traitName)}</strong></td>
                    <td style="max-width:300px;">${escapeHtml(t.description)}</td>
                    <td style="max-width:200px;">${escapeHtml(t.example || '')}</td>
                    <td>
                        <button class="btn" style="padding:4px 8px;" onclick="editTrait(${t.id})">编辑</button>
                        <button class="btn danger" style="padding:4px 8px;" onclick="deleteTrait(${t.id})">删除</button>
                    </td>
                </tr>
            `).join('');
        }
        renderTraitPagination();
    } catch (err) {
        console.error('加载特质失败', err);
        document.getElementById('traits-body').innerHTML = '<tr><td colspan="5">加载失败</td></tr>';
    }
}

function renderTraitPagination() {
    const container = document.getElementById('traits-pagination');
    if (traitTotalPages <= 1) {
        container.innerHTML = '';
        return;
    }
    let html = '';
    for (let i = 0; i < traitTotalPages; i++) {
        html += `<button class="page-btn ${i === traitCurrentPage ? 'active' : ''}" onclick="loadTraits(${i})">${i+1}</button>`;
    }
    container.innerHTML = html;
}

function openTraitModal(trait = null) {
    const modal = document.getElementById('trait-modal');
    const title = document.getElementById('trait-modal-title');
    const idField = document.getElementById('trait-id');
    const nameField = document.getElementById('trait-name');
    const descField = document.getElementById('trait-description');
    const exampleField = document.getElementById('trait-example');

    if (trait) {
        title.innerText = '编辑特质';
        idField.value = trait.id;
        nameField.value = trait.traitName;
        descField.value = trait.description;
        exampleField.value = trait.example || '';
    } else {
        title.innerText = '新增特质';
        idField.value = '';
        nameField.value = '';
        descField.value = '';
        exampleField.value = '';
    }
    modal.style.display = 'block';
}

function closeTraitModal() {
    document.getElementById('trait-modal').style.display = 'none';
}

async function saveTrait(event) {
    event.preventDefault();
    const id = document.getElementById('trait-id').value;
    const traitName = document.getElementById('trait-name').value.trim();
    const description = document.getElementById('trait-description').value.trim();
    const example = document.getElementById('trait-example').value.trim();

    if (!traitName || !description) {
        alert('请填写特质名称和描述');
        return;
    }

    const payload = { traitName, description, example };
    let url = '/v1/game/traits';
    let method = 'POST';
    if (id) {
        url = `/v1/game/traits/${id}`;
        method = 'PUT';
    }

    try {
        const res = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (data.status === 'ok') {
            alert(data.message);
            closeTraitModal();
            loadTraits(traitCurrentPage);
        } else {
            alert('操作失败: ' + data.message);
        }
    } catch (err) {
        console.error('保存特质失败', err);
        alert('网络错误');
    }
}

window.editTrait = async (id) => {
    try {
        const res = await fetch(`/v1/game/traits/${id}`);
        const trait = await res.json();
        openTraitModal(trait);
    } catch (err) {
        console.error('获取特质详情失败', err);
    }
};

window.deleteTrait = async (id) => {
    if (!confirm('确定要删除此特质吗？删除后可能影响现有角色的特质引用。')) return;
    try {
        const res = await fetch(`/v1/game/traits/${id}`, { method: 'DELETE' });
        const data = await res.json();
        if (data.status === 'ok') {
            alert(data.message);
            loadTraits(traitCurrentPage);
        } else {
            alert('删除失败: ' + data.message);
        }
    } catch (err) {
        console.error('删除失败', err);
    }
};

let gameEventsPage = 0;
let gameEventsTotalPages = 1;
let gameEventsFilterSession = '';

async function loadGameEvents(page = 0) {
    gameEventsPage = page;
    const params = new URLSearchParams({
        page,
        size: 10,
        sessionId: gameEventsFilterSession || ''
    });
    try {
        const res = await fetch(`/admin/game-events?${params}`);
        const data = await res.json();
        const events = data.content || [];
        gameEventsTotalPages = data.totalPages || 1;
        const tbody = document.getElementById('game-events-body');
        if (events.length === 0) {
            tbody.innerHTML = '<tr><td colspan="9">暂无数据</td></tr>';
        } else {
            tbody.innerHTML = events.map(e => `
                <tr>
                    <td>${e.id}</td>
                    <td style="font-family:monospace;">${escapeHtml(e.sessionId)}</td>
                    <td>${e.turnNumber}</td>
                    <td>${escapeHtml(e.eventType)}</td>
                    <td style="max-width:150px; overflow:hidden; text-overflow:ellipsis;" title="${escapeHtml(e.userInput || '')}">${escapeHtml(e.userInput ? e.userInput.substring(0,50) : '')}</td>
                    <td style="max-width:150px; overflow:hidden; text-overflow:ellipsis;" title="${escapeHtml(e.aiResponse || '')}">${escapeHtml(e.aiResponse ? e.aiResponse.substring(0,50) : '')}</td>
                    <td>${escapeHtml(e.narrativeMode || '')}</td>
                    <td>${e.processTimeMs}</td>
                    <td>
                        <button class="btn" style="padding:4px 8px;" onclick="viewGameEvent(${e.id})">详情</button>
                        <button class="btn danger" style="padding:4px 8px;" onclick="deleteGameEvent(${e.id})">删除</button>
                    </td>
                </tr>
            `).join('');
        }
        renderGameEventsPagination();
    } catch (err) {
        console.error('加载游戏事件失败', err);
        document.getElementById('game-events-body').innerHTML = '<tr><td colspan="9">加载失败</td></tr>';
    }
}

function renderGameEventsPagination() {
    const container = document.getElementById('game-events-pagination');
    if (gameEventsTotalPages <= 1) {
        container.innerHTML = '';
        return;
    }
    let html = '';
    for (let i = 0; i < gameEventsTotalPages; i++) {
        html += `<button class="page-btn ${i === gameEventsPage ? 'active' : ''}" onclick="loadGameEvents(${i})">${i+1}</button>`;
    }
    container.innerHTML = html;
}

window.viewGameEvent = async (id) => {
    try {
        const res = await fetch(`/admin/game-events/${id}`);
        const event = await res.json();
        // 创建模态框显示详情（可复用现有模态框或新建）
        showDetailModal('游戏事件详情', `
            <div><strong>ID:</strong> ${event.id}</div>
            <div><strong>会话ID:</strong> ${escapeHtml(event.sessionId)}</div>
            <div><strong>回合数:</strong> ${event.turnNumber}</div>
            <div><strong>事件类型:</strong> ${escapeHtml(event.eventType)}</div>
            <div><strong>用户输入:</strong><pre>${escapeHtml(event.userInput || '')}</pre></div>
            <div><strong>AI回复:</strong><pre>${escapeHtml(event.aiResponse || '')}</pre></div>
            <div><strong>状态变化(Delta):</strong><pre>${escapeHtml(event.stateDelta || '')}</pre></div>
            <div><strong>模式:</strong> ${escapeHtml(event.narrativeMode || '')}</div>
            <div><strong>耗时(ms):</strong> ${event.processTimeMs}</div>
            <div><strong>时间:</strong> ${new Date(event.eventTime).toLocaleString()}</div>
        `);
    } catch (err) {
        console.error('获取详情失败', err);
    }
};

window.deleteGameEvent = async (id) => {
    if (!confirm('确定要删除该事件吗？')) return;
    try {
        const res = await fetch(`/admin/game-events/${id}`, { method: 'DELETE' });
        const data = await res.json();
        if (data.result === 'success') {
            alert('删除成功');
            loadGameEvents(gameEventsPage);
        } else {
            alert('删除失败');
        }
    } catch (err) {
        console.error('删除失败', err);
    }
};

function showDetailModal(title, contentHtml) {
    // 检查是否已有模态框，没有则创建
    let modal = document.getElementById('common-detail-modal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'common-detail-modal';
        modal.className = 'modal';
        modal.innerHTML = `
            <div class="modal-content" style="max-width:800px;">
                <span class="close-common">&times;</span>
                <h2></h2>
                <div id="common-detail-content"></div>
            </div>
        `;
        document.body.appendChild(modal);
        modal.querySelector('.close-common').onclick = () => modal.style.display = 'none';
        window.onclick = (e) => { if (e.target === modal) modal.style.display = 'none'; };
    }
    modal.querySelector('h2').innerText = title;
    modal.querySelector('#common-detail-content').innerHTML = contentHtml;
    modal.style.display = 'block';
}

// ---------- 叙事记忆 ----------
let gameMemoriesPage = 0;
let gameMemoriesTotalPages = 1;
let gameMemoriesFilterSession = '';
let gameMemoriesFilterType = '';

async function loadGameMemories(page = 0) {
    gameMemoriesPage = page;
    const params = new URLSearchParams({
        page,
        size: 10,
        sessionId: gameMemoriesFilterSession || '',
        memoryType: gameMemoriesFilterType || ''
    });
    try {
        const res = await fetch(`/admin/game-memories?${params}`);
        const data = await res.json();
        const memories = data.content || [];
        gameMemoriesTotalPages = data.totalPages || 1;
        const tbody = document.getElementById('game-memories-body');
        if (memories.length === 0) {
            tbody.innerHTML = '<tr><td colspan="10">暂无数据</td></tr>';
        } else {
            tbody.innerHTML = memories.map(m => `
                <tr>
                    <td>${m.id}</td>
                    <td style="font-family:monospace;">${escapeHtml(m.sessionId)}</td>
                    <td>${m.turnNumber}</td>
                    <td>${escapeHtml(m.memoryType)}</td>
                    <td style="max-width:200px; overflow:hidden; text-overflow:ellipsis;" title="${escapeHtml(m.content)}">${escapeHtml(m.content ? m.content.substring(0,50) : '')}</td>
                    <td>${m.importance}</td>
                    <td style="max-width:150px; overflow:hidden; text-overflow:ellipsis;" title="${escapeHtml(m.userInput)}">${escapeHtml(m.userInput ? m.userInput.substring(0,40) : '')}</td>
                    <td style="max-width:150px; overflow:hidden; text-overflow:ellipsis;" title="${escapeHtml(m.aiResponse)}">${escapeHtml(m.aiResponse ? m.aiResponse.substring(0,40) : '')}</td>
                    <td>${new Date(m.createdAt).toLocaleString()}</td>
                    <td>
                        <button class="btn" style="padding:4px 8px;" onclick="viewGameMemory(${m.id})">详情</button>
                        <button class="btn danger" style="padding:4px 8px;" onclick="deleteGameMemory(${m.id})">删除</button>
                    </td>
                </tr>
            `).join('');
        }
        renderGameMemoriesPagination();
    } catch (err) {
        console.error('加载叙事记忆失败', err);
        document.getElementById('game-memories-body').innerHTML = '<tr><td colspan="10">加载失败</td></tr>';
    }
}

function renderGameMemoriesPagination() {
    const container = document.getElementById('game-memories-pagination');
    if (gameMemoriesTotalPages <= 1) {
        container.innerHTML = '';
        return;
    }
    let html = '';
    for (let i = 0; i < gameMemoriesTotalPages; i++) {
        html += `<button class="page-btn ${i === gameMemoriesPage ? 'active' : ''}" onclick="loadGameMemories(${i})">${i+1}</button>`;
    }
    container.innerHTML = html;
}

window.viewGameMemory = async (id) => {
    try {
        const res = await fetch(`/admin/game-memories/${id}`);
        const memory = await res.json();
        showDetailModal('叙事记忆详情', `
            <div><strong>ID:</strong> ${memory.id}</div>
            <div><strong>会话ID:</strong> ${escapeHtml(memory.sessionId)}</div>
            <div><strong>回合数:</strong> ${memory.turnNumber}</div>
            <div><strong>记忆类型:</strong> ${escapeHtml(memory.memoryType)}</div>
            <div><strong>内容:</strong><pre>${escapeHtml(memory.content)}</pre></div>
            <div><strong>摘要:</strong><pre>${escapeHtml(memory.summary)}</pre></div>
            <div><strong>重要性:</strong> ${memory.importance}</div>
            <div><strong>用户输入:</strong><pre>${escapeHtml(memory.userInput)}</pre></div>
            <div><strong>AI回复:</strong><pre>${escapeHtml(memory.aiResponse)}</pre></div>
            <div><strong>创建时间:</strong> ${new Date(memory.createdAt).toLocaleString()}</div>
        `);
    } catch (err) {
        console.error('获取详情失败', err);
    }
};

window.deleteGameMemory = async (id) => {
    if (!confirm('确定要删除该叙事记忆吗？')) return;
    try {
        const res = await fetch(`/admin/game-memories/${id}`, { method: 'DELETE' });
        const data = await res.json();
        if (data.result === 'success') {
            alert('删除成功');
            loadGameMemories(gameMemoriesPage);
        } else {
            alert('删除失败');
        }
    } catch (err) {
        console.error('删除失败', err);
    }
};

// ---------- 恢复点 ----------
let gameRecoveryPage = 0;
let gameRecoveryTotalPages = 1;
let gameRecoveryFilterSession = '';

async function loadGameRecoveryPoints(page = 0) {
    gameRecoveryPage = page;
    const params = new URLSearchParams({
        page,
        size: 10,
        sessionId: gameRecoveryFilterSession || ''
    });
    try {
        const res = await fetch(`/admin/game-recovery-points?${params}`);
        const data = await res.json();
        const points = data.content || [];
        gameRecoveryTotalPages = data.totalPages || 1;
        const tbody = document.getElementById('game-recovery-body');
        if (points.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8">暂无数据</td></tr>';
        } else {
            tbody.innerHTML = points.map(p => `
                <tr>
                    <td>${p.id}</td>
                    <td style="font-family:monospace;">${escapeHtml(p.sessionId)}</td>
                    <td>${escapeHtml(p.pointName)}</td>
                    <td style="max-width:200px; overflow:hidden; text-overflow:ellipsis;" title="${escapeHtml(p.description)}">${escapeHtml(p.description ? p.description.substring(0,50) : '')}</td>
                    <td>${p.turnNumber}</td>
                    <td>${new Date(p.createdAt).toLocaleString()}</td>
                    <td>${p.isAuto ? '是' : '否'}</td>
                    <td>
                        <button class="btn" style="padding:4px 8px;" onclick="viewGameRecoveryPoint(${p.id})">详情</button>
                        <button class="btn danger" style="padding:4px 8px;" onclick="deleteGameRecoveryPoint(${p.id})">删除</button>
                    </td>
                </tr>
            `).join('');
        }
        renderGameRecoveryPagination();
    } catch (err) {
        console.error('加载恢复点失败', err);
        document.getElementById('game-recovery-body').innerHTML = '<tr><td colspan="8">加载失败</td></tr>';
    }
}

function renderGameRecoveryPagination() {
    const container = document.getElementById('game-recovery-pagination');
    if (gameRecoveryTotalPages <= 1) {
        container.innerHTML = '';
        return;
    }
    let html = '';
    for (let i = 0; i < gameRecoveryTotalPages; i++) {
        html += `<button class="page-btn ${i === gameRecoveryPage ? 'active' : ''}" onclick="loadGameRecoveryPoints(${i})">${i+1}</button>`;
    }
    container.innerHTML = html;
}

window.viewGameRecoveryPoint = async (id) => {
    try {
        const res = await fetch(`/admin/game-recovery-points/${id}`);
        const point = await res.json();
        showDetailModal('恢复点详情', `
            <div><strong>ID:</strong> ${point.id}</div>
            <div><strong>会话ID:</strong> ${escapeHtml(point.sessionId)}</div>
            <div><strong>存档名称:</strong> ${escapeHtml(point.pointName)}</div>
            <div><strong>描述:</strong> ${escapeHtml(point.description)}</div>
            <div><strong>回合数:</strong> ${point.turnNumber}</div>
            <div><strong>创建时间:</strong> ${new Date(point.createdAt).toLocaleString()}</div>
            <div><strong>是否自动:</strong> ${point.isAuto ? '是' : '否'}</div>
        `);
    } catch (err) {
        console.error('获取详情失败', err);
    }
};

window.deleteGameRecoveryPoint = async (id) => {
    if (!confirm('确定要删除该恢复点吗？')) return;
    try {
        const res = await fetch(`/admin/game-recovery-points/${id}`, { method: 'DELETE' });
        const data = await res.json();
        if (data.result === 'success') {
            alert('删除成功');
            loadGameRecoveryPoints(gameRecoveryPage);
        } else {
            alert('删除失败');
        }
    } catch (err) {
        console.error('删除失败', err);
    }
};

// ---------- 快照管理 ----------
let gameSnapshotsPage = 0;
let gameSnapshotsTotalPages = 1;
let gameSnapshotsFilterSession = '';

async function loadGameSnapshots(page = 0) {
    gameSnapshotsPage = page;
    const params = new URLSearchParams({
        page,
        size: 10,
        sessionId: gameSnapshotsFilterSession || ''
    });
    try {
        const res = await fetch(`/admin/game-snapshots?${params}`);
        const data = await res.json();
        const snapshots = data.content || [];
        gameSnapshotsTotalPages = data.totalPages || 1;
        const tbody = document.getElementById('game-snapshots-body');
        if (snapshots.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7">暂无数据</td></tr>';
        } else {
            tbody.innerHTML = snapshots.map(s => `
                <tr>
                    <td>${s.id}</td>
                    <td style="font-family:monospace;">${escapeHtml(s.sessionId)}</td>
                    <td>${s.turnNumber}</td>
                    <td>${s.version}</td>
                    <td>${new Date(s.snapshotTime).toLocaleString()}</td>
                    <td>${s.isCurrent ? '<span style="color:#10b981;">✅ 是</span>' : '<span style="color:#9ca3af;">❌ 否</span>'}</td>
                    <td>
                        <button class="btn" style="padding:4px 8px;" onclick="viewGameSnapshot(${s.id})">详情</button>
                        ${!s.isCurrent ? `<button class="btn" style="padding:4px 8px;" onclick="setCurrentSnapshot(${s.id})">设为当前</button>` : ''}
                        <button class="btn danger" style="padding:4px 8px;" onclick="deleteGameSnapshot(${s.id})">删除</button>
                    </td>
                </tr>
            `).join('');
        }
        renderGameSnapshotsPagination();
    } catch (err) {
        console.error('加载快照失败', err);
        document.getElementById('game-snapshots-body').innerHTML = '<tr><td colspan="7">加载失败</td></tr>';
    }
}

function renderGameSnapshotsPagination() {
    const container = document.getElementById('game-snapshots-pagination');
    if (gameSnapshotsTotalPages <= 1) {
        container.innerHTML = '';
        return;
    }
    let html = '';
    for (let i = 0; i < gameSnapshotsTotalPages; i++) {
        html += `<button class="page-btn ${i === gameSnapshotsPage ? 'active' : ''}" onclick="loadGameSnapshots(${i})">${i+1}</button>`;
    }
    container.innerHTML = html;
}

window.viewGameSnapshot = async (id) => {
    try {
        const res = await fetch(`/admin/game-snapshots/${id}`);
        const snapshot = await res.json();
        showDetailModal('快照详情', `
            <div><strong>ID:</strong> ${snapshot.id}</div>
            <div><strong>会话ID:</strong> ${escapeHtml(snapshot.sessionId)}</div>
            <div><strong>回合数:</strong> ${snapshot.turnNumber}</div>
            <div><strong>版本:</strong> ${snapshot.version}</div>
            <div><strong>快照时间:</strong> ${new Date(snapshot.snapshotTime).toLocaleString()}</div>
            <div><strong>是否当前:</strong> ${snapshot.isCurrent ? '是' : '否'}</div>
            <div><strong>世界状态:</strong><pre>${escapeHtml(snapshot.worldState)}</pre></div>
            <div><strong>角色状态:</strong><pre>${escapeHtml(snapshot.characterState)}</pre></div>
            <div><strong>剧情状态:</strong><pre>${escapeHtml(snapshot.plotState)}</pre></div>
            <div><strong>叙事记忆:</strong><pre>${escapeHtml(snapshot.narrativeMemory)}</pre></div>
        `);
    } catch (err) {
        console.error('获取详情失败', err);
    }
};

window.setCurrentSnapshot = async (id) => {
    if (!confirm('确定要将此快照设为当前吗？这会影响游戏恢复时的状态。')) return;
    try {
        const res = await fetch(`/admin/game-snapshots/${id}/current`, { method: 'PUT' });
        const data = await res.json();
        if (data.status === 'ok') {
            alert(data.message);
            loadGameSnapshots(gameSnapshotsPage);
        } else {
            alert('操作失败: ' + data.message);
        }
    } catch (err) {
        console.error('设为当前失败', err);
    }
};

window.deleteGameSnapshot = async (id) => {
    if (!confirm('确定要删除该快照吗？')) return;
    try {
        const res = await fetch(`/admin/game-snapshots/${id}`, { method: 'DELETE' });
        const data = await res.json();
        if (data.result === 'success') {
            alert('删除成功');
            loadGameSnapshots(gameSnapshotsPage);
        } else {
            alert('删除失败');
        }
    } catch (err) {
        console.error('删除失败', err);
    }
};