<template>
  <div class="chat-layout">

    <!-- ── 左侧会话列表 ──────────────────────────────────── -->
    <aside class="session-sidebar">
      <div class="session-header">
        <span class="session-title">对话列表</span>
        <el-button type="primary" size="small" :icon="Plus" @click="onNewSession">新建</el-button>
      </div>

      <div class="session-list">
        <div
          v-for="s in sessions"
          :key="s.id"
          class="session-item"
          :class="{ active: s.id === activeSessionId }"
          @click="selectSession(s.id)"
        >
          <div class="session-item-top">
            <span class="session-agent">{{ agentNameMap[s.agentId] ?? 'Agent' }}</span>
            <span class="session-time">{{ formatTime(s.createdAt) }}</span>
          </div>
          <div class="session-preview">{{ sessionPreviewMap[s.id] ?? '暂无消息' }}</div>
          <el-button
            class="session-delete-btn"
            type="danger"
            link
            size="small"
            :icon="Delete"
            @click.stop="onDeleteSession(s)"
          />
        </div>

        <div v-if="sessions.length === 0" class="session-empty">暂无对话</div>
      </div>
    </aside>

    <!-- ── 右侧聊天窗口 ──────────────────────────────────── -->
    <section class="chat-main">

      <!-- 无会话占位 -->
      <div v-if="!activeSessionId" class="chat-welcome">
        <div class="welcome-icon">💬</div>
        <div class="welcome-title">选择或新建一个对话</div>
        <div class="welcome-sub">从左侧列表选择对话，或点击「新建」开始</div>
      </div>

      <template v-else>
        <!-- 顶栏 -->
        <div class="chat-topbar">
          <span class="chat-topbar-name">{{ agentNameMap[activeSession?.agentId ?? 0] ?? 'Agent' }}</span>
          <el-tag v-if="activeAgentWorkflowId" size="small" type="warning" effect="light" style="margin-left:8px">
            工作流模式
          </el-tag>
          <el-tag v-else size="small" type="info" effect="light" style="margin-left:8px">
            直接对话
          </el-tag>
        </div>

        <!-- 消息区域 -->
        <div class="messages-wrap" ref="messagesEl">
          <div
            v-for="msg in messages"
            :key="msg.id ?? msg._tempId"
            class="msg-row"
            :class="msg.role"
          >
            <!-- 头像 -->
            <div class="msg-avatar">
              <el-avatar v-if="msg.role === 'user'" :size="30" style="background:#6366f1;font-size:13px">我</el-avatar>
              <el-avatar v-else :size="30" style="background:#8b5cf6;font-size:13px">AI</el-avatar>
            </div>

            <!-- 气泡 -->
            <div class="msg-bubble" :class="{ error: msg._error, loading: msg._loading }">
              <!-- 加载动画 -->
              <span v-if="msg._loading && !msg.content" class="typing-dots">
                <span /><span /><span />
              </span>
              <!-- assistant 渲染 Markdown，user 纯文本 -->
              <div
                v-else-if="msg.role === 'assistant'"
                class="msg-content markdown-body"
                v-html="renderMarkdown(msg.content)"
              />
              <div v-else class="msg-content">{{ msg.content }}</div>
            </div>
          </div>
        </div>

        <!-- 底部输入区 -->
        <div class="chat-input-area">
          <el-input
            v-model="inputText"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 5 }"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            resize="none"
            :disabled="streaming"
            @keydown.enter.exact.prevent="onSend"
          />
          <el-button
            class="send-btn"
            type="primary"
            :disabled="!inputText.trim() || streaming"
            :loading="streaming"
            @click="onSend"
          >
            {{ streaming ? '生成中' : '发送' }}
          </el-button>
        </div>
      </template>

    </section>

    <!-- 新建对话弹窗 -->
    <el-dialog v-model="newSessionVisible" title="新建对话" width="400px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="选择 Agent">
          <el-select v-model="newSessionAgentId" placeholder="请选择 Agent" style="width:100%">
            <el-option v-for="a in agents" :key="a.id" :value="a.id">
              <div style="display:flex;align-items:center;gap:8px;justify-content:space-between">
                <span>{{ a.name }}</span>
                <div style="display:flex;gap:4px">
                  <el-tag v-if="a.workflowId" size="small" type="warning" effect="light">工作流</el-tag>
                  <el-tag v-if="a.knowledgeBaseId" size="small" type="success" effect="light">知识库</el-tag>
                </div>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newSessionVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!newSessionAgentId" @click="confirmNewSession">确定</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { marked } from 'marked'
import {
  createSession, getSessions, deleteSession,
  getMessages, streamMessage,
} from '@/api/chat'
import type { ChatSession, ChatMessage } from '@/api/chat'
import { getAgentList } from '@/api/agent'
import type { AgentListItem } from '@/api/agent'

// ── marked 配置 ───────────────────────────────────────────
marked.setOptions({ breaks: true })

// ── 消息类型（含临时字段）────────────────────────────────
interface DisplayMessage extends Partial<ChatMessage> {
  _tempId?: string
  _loading?: boolean
  _error?: boolean
  role: 'user' | 'assistant' | 'system'
  content: string
}

// ── 状态 ─────────────────────────────────────────────────
const sessions = ref<ChatSession[]>([])
const activeSessionId = ref<number | null>(null)
const messages = ref<DisplayMessage[]>([])
const inputText = ref('')
const streaming = ref(false)
const agents = ref<AgentListItem[]>([])

const newSessionVisible = ref(false)
const newSessionAgentId = ref<number | null>(null)

const messagesEl = ref<HTMLElement>()

const sessionPreviewMap = ref<Record<number, string>>({})

const agentNameMap = computed<Record<number, string>>(() => {
  const m: Record<number, string> = {}
  for (const a of agents.value) m[a.id] = a.name
  return m
})

const activeSession = computed(() => sessions.value.find(s => s.id === activeSessionId.value))

const activeAgentWorkflowId = computed(() => {
  const agentId = activeSession.value?.agentId
  if (!agentId) return null
  return agents.value.find(a => a.id === agentId)?.workflowId ?? null
})

// ── 初始化 ────────────────────────────────────────────────
onMounted(async () => {
  await Promise.all([loadSessions(), loadAgents()])
  if (sessions.value.length > 0) {
    await selectSession(sessions.value[0].id)
  }
})

async function loadSessions() {
  try {
    const res = await getSessions({ pageSize: 50 })
    sessions.value = res.list
  } catch { /* ignore */ }
}

async function loadAgents() {
  try {
    const res = await getAgentList({ page: 1, pageSize: 100 })
    agents.value = res.list
  } catch { /* ignore */ }
}

// ── 切换会话 ──────────────────────────────────────────────
async function selectSession(id: number) {
  if (streaming.value) return
  activeSessionId.value = id
  messages.value = []
  try {
    const res = await getMessages(id, { page: 1, pageSize: 100 })
    messages.value = res.list.map(m => ({ ...m, role: m.role as 'user' | 'assistant' | 'system' }))
    const last = res.list[res.list.length - 1]
    if (last) sessionPreviewMap.value[id] = truncate(last.content, 30)
    await scrollToBottom()
  } catch { /* ignore */ }
}

// ── 新建会话 ──────────────────────────────────────────────
function onNewSession() {
  newSessionAgentId.value = agents.value[0]?.id ?? null
  newSessionVisible.value = true
}

async function confirmNewSession() {
  if (!newSessionAgentId.value) return
  try {
    const s = await createSession(newSessionAgentId.value)
    sessions.value.unshift(s)
    newSessionVisible.value = false
    await selectSession(s.id)
  } catch {
    ElMessage.error('创建对话失败')
  }
}

// ── 删除会话 ──────────────────────────────────────────────
async function onDeleteSession(s: ChatSession) {
  try {
    await ElMessageBox.confirm('确定删除这个对话？', '提示', { type: 'warning' })
    await deleteSession(s.id)
    sessions.value = sessions.value.filter(x => x.id !== s.id)
    delete sessionPreviewMap.value[s.id]
    if (activeSessionId.value === s.id) {
      activeSessionId.value = null
      messages.value = []
      if (sessions.value.length > 0) await selectSession(sessions.value[0].id)
    }
  } catch { /* cancel */ }
}

// ── 发送消息 ──────────────────────────────────────────────
async function onSend() {
  const content = inputText.value.trim()
  if (!content || !activeSessionId.value || streaming.value) return

  inputText.value = ''
  streaming.value = true

  // 用户气泡
  const userMsg: DisplayMessage = { _tempId: uid(), role: 'user', content }
  messages.value.push(userMsg)
  await scrollToBottom()

  // AI 气泡（loading）
  const aiMsg: DisplayMessage = { _tempId: uid(), role: 'assistant', content: '', _loading: true }
  messages.value.push(aiMsg)
  await scrollToBottom()

  const sessionId = activeSessionId.value
  streamMessage(
    sessionId,
    content,
    (delta) => {
      aiMsg.content += delta
      aiMsg._loading = false
      scrollToBottom()
    },
    (_finishReason, _latencyMs) => {
      aiMsg._loading = false
      streaming.value = false
      sessionPreviewMap.value[sessionId] = truncate(aiMsg.content, 30)
    },
    (errMsg) => {
      aiMsg._loading = false
      aiMsg._error = true
      aiMsg.content = errMsg || 'LLM 调用失败'
      streaming.value = false
    },
  )
}

// ── 工具函数 ──────────────────────────────────────────────
function renderMarkdown(text: string): string {
  if (!text) return ''
  return marked.parse(text) as string
}

async function scrollToBottom() {
  await nextTick()
  if (messagesEl.value) messagesEl.value.scrollTop = messagesEl.value.scrollHeight
}

function formatTime(iso: string): string {
  const d = new Date(iso)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}分钟前`
  if (d.toDateString() === now.toDateString()) return d.toTimeString().slice(0, 5)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function truncate(text: string, len: number) {
  return text.length > len ? text.slice(0, len) + '…' : text
}

let _uid = 0
function uid() { return `tmp-${++_uid}` }
</script>

<style scoped>
/* ── 整体布局 ─────────────────────────────────────────────── */
.chat-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
  background: var(--color-bg-page);
}

/* ── 左侧会话列表 ─────────────────────────────────────────── */
.session-sidebar {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--color-border-default);
  background: var(--color-bg-card);
}

.session-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 14px 10px;
  border-bottom: 1px solid var(--color-border-default);
  flex-shrink: 0;
}

.session-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px 0;
}

.session-item {
  position: relative;
  padding: 10px 14px 10px 12px;
  cursor: pointer;
  border-left: 3px solid transparent;
  transition: background 0.15s;
}
.session-item:hover { background: var(--color-bg-hover); }
.session-item.active {
  background: rgba(99, 102, 241, 0.08);
  border-left-color: var(--color-primary);
}
.session-item:hover .session-delete-btn { opacity: 1; }

.session-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 3px;
}
.session-agent {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
}
.session-time {
  font-size: 11px;
  color: var(--color-text-tertiary);
}
.session-preview {
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding-right: 20px;
}
.session-delete-btn {
  position: absolute;
  right: 8px;
  bottom: 10px;
  opacity: 0;
  transition: opacity 0.15s;
  padding: 0 !important;
}

.session-empty {
  text-align: center;
  padding: 40px 0;
  color: var(--color-text-tertiary);
  font-size: 13px;
}

/* ── 右侧聊天区 ───────────────────────────────────────────── */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-welcome {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--color-text-secondary);
}
.welcome-icon { font-size: 48px; line-height: 1; }
.welcome-title { font-size: 16px; font-weight: 600; color: var(--color-text-primary); }
.welcome-sub { font-size: 13px; }

.chat-topbar {
  height: 48px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 0 20px;
  border-bottom: 1px solid var(--color-border-default);
  background: var(--color-bg-card);
}
.chat-topbar-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.messages-wrap {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.msg-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.msg-row.user { flex-direction: row-reverse; }

.msg-avatar { flex-shrink: 0; margin-top: 2px; }

.msg-bubble {
  max-width: 68%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.65;
  word-break: break-word;
  background: var(--color-bg-card);
  color: var(--color-text-primary);
  border: 1px solid var(--color-border-default);
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.msg-row.user .msg-bubble {
  background: var(--color-primary-500);
  color: #ffffff;
  border-color: transparent;
}
.msg-bubble.error {
  background: rgba(239,68,68,0.08);
  border-color: rgba(239,68,68,0.3);
  color: #ef4444;
}

/* 打字动画 */
.typing-dots {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 2px 0;
}
.typing-dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-text-tertiary);
  animation: blink 1.2s infinite;
}
.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink {
  0%, 80%, 100% { opacity: 0.2; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1); }
}

/* 底部输入区 */
.chat-input-area {
  flex-shrink: 0;
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 14px 20px;
  border-top: 1px solid var(--color-border-default);
  background: var(--color-bg-card);
}
.chat-input-area :deep(.el-textarea__inner) {
  border-radius: 8px;
  font-size: 14px;
  padding: 10px 14px;
  resize: none;
}
.send-btn {
  flex-shrink: 0;
  height: 38px;
  padding: 0 18px;
}

/* ── Markdown 样式 ─────────────────────────────────────────── */
.markdown-body :deep(p) { margin: 0 0 8px; }
.markdown-body :deep(p:last-child) { margin-bottom: 0; }
.markdown-body :deep(code) {
  font-family: 'Fira Code', Consolas, monospace;
  font-size: 13px;
  background: rgba(99,102,241,0.1);
  padding: 1px 5px;
  border-radius: 4px;
}
.markdown-body :deep(pre) {
  background: #1a1b26;
  border-radius: 8px;
  padding: 14px 16px;
  overflow-x: auto;
  margin: 8px 0;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  color: #c0caf5;
  font-size: 13px;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) { padding-left: 20px; margin: 6px 0; }
.markdown-body :deep(li) { margin: 3px 0; }
.markdown-body :deep(strong) { font-weight: 600; }
.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--color-primary);
  margin: 8px 0;
  padding: 4px 12px;
  color: var(--color-text-secondary);
}
</style>
