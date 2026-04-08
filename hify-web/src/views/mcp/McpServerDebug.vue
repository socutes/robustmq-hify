<template>
  <div class="page-container">
    <!-- 顶部导航 -->
    <div class="page-header">
      <div class="page-header-left">
        <el-button text @click="router.push('/mcp')" class="back-btn">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <div>
          <div class="page-title">{{ server?.name || '调试工具' }}</div>
          <div class="page-desc">{{ server?.endpoint }}</div>
        </div>
      </div>
      <div class="page-header-actions">
        <el-tag :type="connectionStatus === 'ok' ? 'success' : connectionStatus === 'fail' ? 'danger' : 'info'" size="small">
          {{ connectionStatus === 'ok' ? '已连接' : connectionStatus === 'fail' ? '无法连接' : '未检测' }}
        </el-tag>
        <el-button size="small" :loading="loadingTools" @click="loadTools" style="margin-left:8px">
          <el-icon><Refresh /></el-icon>
          刷新工具
        </el-button>
      </div>
    </div>

    <div class="debug-layout">
      <!-- 左侧：工具选择 + 参数 -->
      <div class="left-panel">
        <!-- Server 信息卡 -->
        <div class="info-card">
          <div class="info-title">Server 信息</div>
          <div class="info-row">
            <span class="info-label">名称</span>
            <span class="info-value">{{ server?.name }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">Endpoint</span>
            <span class="info-value mono">{{ server?.endpoint }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">描述</span>
            <span class="info-value">{{ server?.description || '—' }}</span>
          </div>
        </div>

        <!-- 工具列表 / 手动输入 -->
        <div class="panel-card">
          <div class="panel-card-header">
            <span class="panel-card-title">选择工具</span>
            <el-button text size="small" @click="manualMode = !manualMode">
              {{ manualMode ? '从列表选择' : '手动输入' }}
            </el-button>
          </div>

          <!-- 手动输入模式 -->
          <div v-if="manualMode" class="tool-input-wrap">
            <el-input
              v-model="toolName"
              placeholder="输入工具名，如 refund_order"
              clearable
            />
          </div>

          <!-- 工具列表模式 -->
          <div v-else>
            <div v-if="loadingTools" class="tool-loading">
              <el-skeleton :rows="3" animated />
            </div>
            <div v-else-if="tools.length === 0" class="tool-empty">
              <el-icon><WarningFilled /></el-icon>
              <span>无法获取工具列表，可切换为手动输入</span>
            </div>
            <div v-else class="tool-list">
              <div
                v-for="tool in tools"
                :key="tool.name"
                class="tool-item"
                :class="{ active: toolName === tool.name }"
                @click="selectTool(tool)"
              >
                <el-icon class="ti-icon"><Tools /></el-icon>
                <div class="ti-info">
                  <div class="ti-name">{{ tool.name }}</div>
                  <div class="ti-desc">{{ tool.description || '无描述' }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 参数面板 -->
        <div class="panel-card">
          <div class="panel-card-header">
            <span class="panel-card-title">输入参数</span>
            <el-button text size="small" @click="paramMode = paramMode === 'form' ? 'json' : 'form'">
              {{ paramMode === 'form' ? 'JSON 模式' : '表单模式' }}
            </el-button>
          </div>

          <!-- 表单模式 -->
          <div v-if="paramMode === 'form'">
            <div v-if="paramEntries.length === 0" class="param-hint">
              <span v-if="!selectedTool && !manualMode">请先选择工具</span>
              <span v-else>该工具无需参数</span>
            </div>
            <div v-else class="param-form">
              <div v-for="param in paramEntries" :key="param.name" class="param-row">
                <div class="param-label-row">
                  <span class="param-name">{{ param.name }}</span>
                  <el-tag v-if="param.required" size="small" type="danger" effect="plain">必填</el-tag>
                  <el-tag size="small" effect="plain">{{ param.type }}</el-tag>
                </div>
                <div v-if="param.description" class="param-desc">{{ param.description }}</div>
                <el-input-number
                  v-if="param.type === 'number' || param.type === 'integer'"
                  v-model="formValues[param.name]"
                  :placeholder="param.name"
                  style="width:100%"
                  controls-position="right"
                />
                <el-input
                  v-else
                  v-model="formValues[param.name]"
                  :placeholder="param.description || param.name"
                />
              </div>
            </div>
          </div>

          <!-- JSON 模式 -->
          <div v-else>
            <el-input
              v-model="jsonArgs"
              type="textarea"
              :rows="6"
              placeholder='{"param1": "value1"}'
              style="font-family: monospace; font-size: 13px"
            />
            <div v-if="jsonError" class="json-error">{{ jsonError }}</div>
          </div>
        </div>

        <!-- 执行按钮 -->
        <el-button
          type="primary"
          size="large"
          :loading="calling"
          :disabled="!toolName.trim()"
          @click="callTool"
          style="width:100%"
        >
          <el-icon><VideoPlay /></el-icon>
          执行调用
        </el-button>
      </div>

      <!-- 右侧：结果 -->
      <div class="right-panel">
        <div class="panel-card result-panel">
          <div class="panel-card-header">
            <span class="panel-card-title">调用结果</span>
            <span v-if="history.length" class="history-count">{{ history.length }} 条记录</span>
          </div>

          <div v-if="history.length === 0" class="result-empty">
            <el-icon :size="36"><VideoPlay /></el-icon>
            <p>填写参数后点击「执行调用」</p>
          </div>

          <div v-else class="history-list">
            <div
              v-for="(item, idx) in history"
              :key="idx"
              class="history-item"
              :class="{ 'is-error': !item.success, 'is-active': idx === 0 }"
            >
              <!-- 头部 -->
              <div class="hi-header">
                <div class="hi-status">
                  <el-icon v-if="item.success" class="status-icon ok"><CircleCheck /></el-icon>
                  <el-icon v-else class="status-icon err"><CircleClose /></el-icon>
                  <span class="hi-tool">{{ item.toolName }}</span>
                </div>
                <div class="hi-meta">
                  <el-tag size="small" :type="item.success ? 'success' : 'danger'" effect="plain">
                    {{ item.elapsedMs }}ms
                  </el-tag>
                  <span class="hi-time">{{ item.time }}</span>
                </div>
              </div>

              <!-- 参数 -->
              <div class="hi-section">
                <div class="hi-label">参数</div>
                <code class="hi-args">{{ JSON.stringify(item.arguments) }}</code>
              </div>

              <!-- 结果 -->
              <div class="hi-section">
                <div class="hi-label">{{ item.success ? '返回结果' : '错误信息' }}</div>
                <pre class="hi-result" :class="{ 'hi-result--error': !item.success }">{{ item.success ? item.result : item.errorMessage }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Tools, VideoPlay, CircleCheck, CircleClose, Refresh, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getMcpServerDetail, getMcpServerTools, debugMcpTool } from '@/api/mcp'
import type { McpToolDetail, McpServerVO } from '@/api/mcp'

const route = useRoute()
const router = useRouter()
const serverId = Number(route.params.id)

// ── 服务器信息 ──────────────────────────────────────────────
const server = ref<McpServerVO | null>(null)

// ── 工具列表 ─────────────────────────────────────────────────
const tools = ref<McpToolDetail[]>([])
const loadingTools = ref(false)
const connectionStatus = ref<'unknown' | 'ok' | 'fail'>('unknown')
const manualMode = ref(false)

// ── 选中工具 ─────────────────────────────────────────────────
const toolName = ref('')
const selectedTool = ref<McpToolDetail | null>(null)

// ── 参数 ─────────────────────────────────────────────────────
const paramMode = ref<'form' | 'json'>('form')
const formValues = ref<Record<string, unknown>>({})
const jsonArgs = ref('{}')
const jsonError = ref('')

// ── 调用历史 ─────────────────────────────────────────────────
interface HistoryItem {
  toolName: string
  time: string
  success: boolean
  result: string | null
  errorMessage: string | null
  elapsedMs: number
  arguments: Record<string, unknown>
}
const history = ref<HistoryItem[]>([])
const calling = ref(false)

// ── 计算参数列表（从选中工具的 schema 提取）──────────────────
const paramEntries = computed(() => {
  if (!selectedTool.value?.inputSchema?.properties) return []
  const required = selectedTool.value.requiredParams ?? []
  return Object.entries(selectedTool.value.inputSchema.properties).map(([name, schema]) => ({
    name,
    type: (schema as any).type ?? 'string',
    description: (schema as any).description as string | undefined,
    required: required.includes(name),
  }))
})

// ── 操作 ─────────────────────────────────────────────────────
const selectTool = (tool: McpToolDetail) => {
  selectedTool.value = tool
  toolName.value = tool.name
  formValues.value = {}
  jsonArgs.value = '{}'
}

const loadTools = async () => {
  loadingTools.value = true
  try {
    const list = await getMcpServerTools(serverId)
    tools.value = list
    if (list.length > 0) {
      connectionStatus.value = 'ok'
      if (!toolName.value) selectTool(list[0])
    } else {
      // 连不上时后端返回空列表
      connectionStatus.value = 'fail'
      manualMode.value = true
    }
  } catch {
    connectionStatus.value = 'fail'
    manualMode.value = true
  } finally {
    loadingTools.value = false
  }
}

const buildArguments = (): Record<string, unknown> | null => {
  if (paramMode.value === 'json') {
    try {
      jsonError.value = ''
      return JSON.parse(jsonArgs.value || '{}')
    } catch {
      jsonError.value = 'JSON 格式错误'
      return null
    }
  }
  return { ...formValues.value }
}

const callTool = async () => {
  if (!toolName.value.trim()) return

  // 必填校验（表单模式）
  if (paramMode.value === 'form') {
    for (const param of paramEntries.value) {
      if (param.required && (formValues.value[param.name] === undefined || formValues.value[param.name] === '')) {
        ElMessage.warning(`参数「${param.name}」不能为空`)
        return
      }
    }
  }

  const args = buildArguments()
  if (args === null) return

  calling.value = true
  try {
    const res = await debugMcpTool(serverId, { toolName: toolName.value.trim(), arguments: args })
    history.value.unshift({
      toolName: toolName.value.trim(),
      time: new Date().toLocaleTimeString(),
      success: res.success,
      result: res.result,
      errorMessage: res.errorMessage,
      elapsedMs: res.elapsedMs,
      arguments: args,
    })
    if (history.value.length > 5) history.value.pop()
  } finally {
    calling.value = false
  }
}

onMounted(async () => {
  try {
    server.value = await getMcpServerDetail(serverId)
  } catch {
    ElMessage.error('获取 Server 信息失败')
  }
  await loadTools()
})
</script>

<style scoped>
.back-btn {
  color: var(--color-text-secondary);
  margin-right: 4px;
  padding: 0;
}

/* ── 整体布局 ──────────────────────────────────────────────── */
.debug-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.left-panel {
  width: 360px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.right-panel {
  flex: 1;
  min-width: 0;
}

/* ── 卡片 ──────────────────────────────────────────────────── */
.info-card,
.panel-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border-default);
  border-radius: 10px;
  padding: 16px;
}

.info-title {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-tertiary);
  margin-bottom: 10px;
}

.info-row {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
  align-items: flex-start;
}
.info-label {
  font-size: 12px;
  color: var(--color-text-tertiary);
  width: 48px;
  flex-shrink: 0;
  padding-top: 1px;
}
.info-value {
  font-size: 13px;
  color: var(--color-text-primary);
  word-break: break-all;
}
.info-value.mono {
  font-family: monospace;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.panel-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.panel-card-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
}

/* ── 工具列表 ──────────────────────────────────────────────── */
.tool-loading { padding: 4px 0; }

.tool-empty {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-tertiary);
  padding: 8px 0;
}
.tool-empty .el-icon { color: var(--el-color-warning); }

.tool-input-wrap { padding: 4px 0; }

.tool-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 220px;
  overflow-y: auto;
}

.tool-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: background 0.15s;
}
.tool-item:hover { background: var(--color-bg-hover); }
.tool-item.active {
  background: rgba(99,102,241,0.08);
  border-color: rgba(99,102,241,0.25);
}

.ti-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--color-primary);
  font-size: 14px;
}
.ti-info { overflow: hidden; }
.ti-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  font-family: monospace;
}
.ti-desc {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── 参数面板 ──────────────────────────────────────────────── */
.param-hint {
  font-size: 13px;
  color: var(--color-text-tertiary);
  padding: 4px 0 8px;
}

.param-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.param-row { display: flex; flex-direction: column; gap: 5px; }

.param-label-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.param-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  font-family: monospace;
}

.param-desc {
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.json-error {
  font-size: 12px;
  color: var(--el-color-danger);
  margin-top: 4px;
}

/* ── 结果面板 ──────────────────────────────────────────────── */
.result-panel {
  min-height: 400px;
}

.result-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  gap: 12px;
  color: var(--color-text-tertiary);
  font-size: 14px;
}
.result-empty .el-icon { opacity: 0.4; }

.history-count {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-item {
  border: 1px solid var(--color-border-default);
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: border-color 0.2s;
}
.history-item.is-active {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(99,102,241,0.08);
}
.history-item.is-error {
  border-color: rgba(239,68,68,0.3);
}

.hi-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.hi-status {
  display: flex;
  align-items: center;
  gap: 6px;
}
.status-icon { font-size: 16px; }
.status-icon.ok  { color: var(--el-color-success); }
.status-icon.err { color: var(--el-color-danger); }
.hi-tool {
  font-size: 14px;
  font-weight: 600;
  font-family: monospace;
  color: var(--color-text-primary);
}
.hi-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hi-time {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.hi-section { display: flex; flex-direction: column; gap: 4px; }
.hi-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-tertiary);
}
.hi-args {
  font-size: 12px;
  font-family: monospace;
  color: var(--color-text-secondary);
  word-break: break-all;
  background: var(--color-bg-page);
  padding: 6px 8px;
  border-radius: 4px;
}
.hi-result {
  margin: 0;
  font-size: 13px;
  font-family: monospace;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--color-text-primary);
  background: var(--color-bg-page);
  padding: 10px 12px;
  border-radius: 6px;
  max-height: 300px;
  overflow-y: auto;
  line-height: 1.6;
}
.hi-result--error {
  color: var(--el-color-danger);
  background: rgba(239,68,68,0.05);
}
</style>
