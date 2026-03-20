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

// 初始化
document.addEventListener('DOMContentLoaded', () => {
    // 1. 主标签页切换 (仪表盘 / 会话 / 记忆)
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
        });
    });

    // 2. 【新增】仪表盘内部状态切换 (Ollama / 会话统计 / 记忆统计 / 调试快照)
    document.querySelectorAll('.status-tab-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            // 切换按钮样式
            document.querySelectorAll('.status-tab-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            // 切换内容面板
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

    // 弹窗关闭
    document.querySelector('.close').addEventListener('click', () => {
        document.getElementById('session-detail-modal').style.display = 'none';
    });
    window.addEventListener('click', (e) => {
        if (e.target.classList.contains('modal')) {
            e.target.style.display = 'none';
        }
    });
});

// ---------- 仪表盘 ----------
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

        // 显示Ollama状态
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
        // 错误提示可在此处添加，但不改变功能逻辑
    }
}

// ---------- 会话管理 ----------
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

// 查看会话详情
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
                <div style="white-space:pre-wrap; color:#1f2937;">${h.content}</div>
            </div>
        `).join('');

        document.getElementById('detail-history').innerHTML = `<h4 style="margin-bottom:10px; color:#374151;">对话历史</h4>${historyHtml || '<div style="color:#9ca3af">暂无记录</div>'}`;

        document.getElementById('detail-memory').innerHTML = `<h4 style="margin-bottom:10px; color:#374151; margin-top:20px;">关联记忆上下文</h4><div style="white-space:pre-wrap; background:#f9fafb; padding:10px; border-radius:6px; border:1px solid #e5e7eb;">${data.memoryContext || '无'}</div>`;

        document.getElementById('session-detail-modal').style.display = 'block';
    } catch (err) {
        console.error('加载会话详情失败', err);
    }
};

// 结束会话
window.endSession = async (sessionId) => {
    if (!confirm(`确定要结束会话 ${sessionId} 吗？`)) return;
    try {
        await fetch(`${API_BASE}/sessions/${sessionId}`, { method: 'DELETE' });
        loadSessions();
    } catch (err) {
        console.error('结束会话失败', err);
    }
};

// ---------- 记忆管理 ----------
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
                    <td style="max-width:300px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;" title="${m.content.replace(/"/g, '&quot;')}">${m.content.substring(0, 60)}${m.content.length > 60 ? '...' : ''}</td>
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