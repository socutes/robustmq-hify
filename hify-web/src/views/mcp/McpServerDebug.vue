<template>
  <div class="page-container">
    <!-- 顶部面包屑 -->
    <div class="page-header">
      <div class="page-header-left">
        <el-button text @click="router.push('/mcp')" class="back-btn">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <div class="page-title">{{ serverName }} · 调试工具</div>
        <div class="page-desc">选择工具，填写参数，实时调用验证</div>
      </div>
    </div>

    <div v-if="loading" class="loading-wrap">
      <el-skeleton :rows="6" animated />
    </div>

    <div v-else-if="tools.length === 0" class="empty-wrap">
      <el-empty description="该 Server 暂未声明工具，或无法连接" />
    </div>

    <div v-else class="debug-layout">
      <!-- 左侧工具列表 -->
      <div class="tool-sidebar">
        <div class="sidebar-title">工具列表</div>
        <div
          v-for="tool in tools"
          :key="tool.name"
          class="tool-entry"
          :class="{ active: selectedTool?.name === tool.name }"
          @click="selectTool(tool)"
        >
          <el-icon class="tool-icon"><Tools /></el-icon>
          <div class="tool-entry-info">
            <div class="tool-name">{{ tool.name }}</div>
            <div class="tool-desc">{{ tool.description || '无描述' }}</div>
          </div>
        </div>
      </div>

      <!-- 右侧调试面板 -->
      <div class="debug-panel">
        <div v-if="!selectedTool" class="debug-empty">
          <el-icon :size="40" style="color: var(--color-text-tertiary)"><Tools /></el-icon>
          <p>选择左侧工具开始调试</p>
        </div>

        <template v-else>
          <!-- 工具描述 -->
          <div class="debug-section">
            <div class="section-label">工具描述</div>
            <div class="tool-description">{{ selectedTool.description || '无描述' }}</div>
          </div>

          <!-- 参数表单 -->
          <div class="debug-section">
            <div class="section-label">输入参数</div>
            <div v-if="!paramEntries.length" class="no-params">该工具无需参数</div>
            <div v-else class="param-form">
              <div v-for="param in paramEntries" :key="param.name" class="param-row">
                <div class="param-label">
                  <span class="param-name">{{ param.name }}</span>
                  <el-tag v-if="param.required" size="small" type="danger" effect="plain">必填</el-tag>
                  <el-tag size="small" effect="plain" style="margin-left:4px">{{ param.type }}</el-tag>
                </div>
                <div v-if="param.description" class="param-desc">{{ param.description }}</div>
                <el-input-number
                  v-if="param.type === 'number' || param.type === 'integer'"
                  v-model="paramValues[param.name]"
                  :placeholder="param.description || param.name"
                  style="width:100%"
                />
                <el-input
                  v-else
                  v-model="paramValues[param.name]"
                  :placeholder="param.description || param.name"
                />
              </div>
            </div>
          </div>

          <!-- 调用按钮 -->
          <div class="debug-action">
            <el-button type="primary" :loading="calling" @click="callTool">
              <el-icon><VideoPlay /></el-icon>
              执行调用
            </el-button>
          </div>

          <!-- 调用历史 -->
          <div v-if="history.length" class="debug-section">
            <div class="section-label">
              调用记录
              <span class="history-hint">（最近 {{ history.length }} 次）</span>
            </div>
            <div class="history-list">
              <div
                v-for="(item, idx) in history"
                :key="idx"
                class="history-item"
                :class="{ 'history-item--error': !item.success }"
              >
                <div class="history-meta">
                  <el-icon v-if="item.success" class="meta-icon success"><CircleCheck /></el-icon>
                  <el-icon v-else class="meta-icon fail"><CircleClose /></el-icon>
                  <span class="meta-time">{{ item.time }}</span>
                  <el-tag size="small" :type="item.success ? 'success' : 'danger'" effect="plain" style="margin-left:auto">
                    {{ item.elapsedMs }}ms
                  </el-tag>
                </div>
                <div class="history-args">
                  <span class="history-label">参数</span>
                  <code>{{ JSON.stringify(item.arguments) }}</code>
                </div>
                <div class="history-result">
                  <span class="history-label">结果</span>
                  <pre class="result-pre">{{ item.success ? item.result : item.errorMessage }}</pre>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Tools, VideoPlay, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getMcpServerDetail, getMcpServerTools, debugMcpTool } from '@/api/mcp'
import type { McpToolDetail } from '@/api/mcp'

const route = useRoute()
const router = useRouter()

const serverId = Number(route.params.id)
const serverName = ref('')
const tools = ref<McpToolDetail[]>([])
const loading = ref(true)
const selectedTool = ref<McpToolDetail | null>(null)
const paramValues = ref<Record<string, unknown>>({})
const calling = ref(false)

interface HistoryItem {
  time: string
  success: boolean
  result: string | null
  errorMessage: string | null
  elapsedMs: number
  arguments: Record<string, unknown>
}
const history = ref<HistoryItem[]>([])

// 从 inputSchema 解析出参数列表
const paramEntries = computed(() => {
  if (!selectedTool.value?.inputSchema?.properties) return []
  const required = selectedTool.value.requiredParams ?? []
  return Object.entries(selectedTool.value.inputSchema.properties).map(([name, schema]) => ({
    name,
    type: schema.type ?? 'string',
    description: schema.description,
    required: required.includes(name),
  }))
})

const selectTool = (tool: McpToolDetail) => {
  selectedTool.value = tool
  paramValues.value = {}
  history.value = []
}

const callTool = async () => {
  if (!selectedTool.value) return

  // 简单必填校验
  for (const param of paramEntries.value) {
    if (param.required && (paramValues.value[param.name] === undefined || paramValues.value[param.name] === '')) {
      ElMessage.warning(`参数「${param.name}」不能为空`)
      return
    }
  }

  calling.value = true
  try {
    const args = { ...paramValues.value }
    const res = await debugMcpTool(serverId, {
      toolName: selectedTool.value.name,
      arguments: args as Record<string, unknown>,
    })

    const item: HistoryItem = {
      time: new Date().toLocaleTimeString(),
      success: res.success,
      result: res.result,
      errorMessage: res.errorMessage,
      elapsedMs: res.elapsedMs,
      arguments: args as Record<string, unknown>,
    }
    // 最多保留最近 5 条，新的在前
    history.value.unshift(item)
    if (history.value.length > 5) history.value.pop()
  } finally {
    calling.value = false
  }
}

onMounted(async () => {
  try {
    const [serverRes, toolsRes] = await Promise.all([
      getMcpServerDetail(serverId),
      getMcpServerTools(serverId),
    ])
    serverName.value = serverRes.name
    tools.value = toolsRes
    if (toolsRes.length > 0) selectTool(toolsRes[0])
  } catch (e) {
    ElMessage.error('加载失败，请检查 MCP Server 是否可连接')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.back-btn {
  margin-right: 8px;
  color: var(--color-text-secondary);
}

.loading-wrap,
.empty-wrap {
  padding: 60px 0;
  display: flex;
  justify-content: center;
}

/* ── 布局 ──────────────────────────────────────────────────── */
.debug-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  height: calc(100vh - 200px);
}

/* ── 左侧工具列表 ────────────────────────────────────────────── */
.tool-sidebar {
  width: 240px;
  flex-shrink: 0;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border-default);
  border-radius: 10px;
  overflow-y: auto;
  height: 100%;
}

.sidebar-title {
  padding: 12px 16px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  border-bottom: 1px solid var(--color-border-default);
}

.tool-entry {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  cursor: pointer;
  border-left: 3px solid transparent;
  transition: background-color 0.15s, border-color 0.15s;
}
.tool-entry:hover {
  background: var(--color-bg-hover);
}
.tool-entry.active {
  background: rgba(99, 102, 241, 0.08);
  border-left-color: var(--color-primary);
}

.tool-icon {
  margin-top: 2px;
  flex-shrink: 0;
  color: var(--color-primary);
  font-size: 15px;
}

.tool-entry-info { overflow: hidden; }
.tool-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tool-desc {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── 右侧面板 ────────────────────────────────────────────────── */
.debug-panel {
  flex: 1;
  min-width: 0;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border-default);
  border-radius: 10px;
  padding: 20px 24px;
  overflow-y: auto;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.debug-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--color-text-tertiary);
  font-size: 14px;
}

.debug-section { display: flex; flex-direction: column; gap: 10px; }

.section-label {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-tertiary);
}
.history-hint {
  font-size: 11px;
  font-weight: 400;
  text-transform: none;
  letter-spacing: 0;
}

.tool-description {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.6;
  padding: 10px 12px;
  background: var(--color-bg-page);
  border-radius: 6px;
}

/* ── 参数表单 ──────────────────────────────────────────────── */
.no-params {
  font-size: 13px;
  color: var(--color-text-tertiary);
  padding: 8px 0;
}

.param-form { display: flex; flex-direction: column; gap: 14px; }

.param-row { display: flex; flex-direction: column; gap: 6px; }

.param-label {
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

/* ── 调用按钮 ──────────────────────────────────────────────── */
.debug-action { display: flex; gap: 10px; }

/* ── 调用历史 ──────────────────────────────────────────────── */
.history-list { display: flex; flex-direction: column; gap: 12px; }

.history-item {
  border: 1px solid var(--color-border-default);
  border-radius: 8px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: var(--color-bg-page);
}
.history-item--error {
  border-color: rgba(239, 68, 68, 0.3);
  background: rgba(239, 68, 68, 0.04);
}

.history-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.meta-icon { font-size: 14px; }
.meta-icon.success { color: var(--el-color-success); }
.meta-icon.fail    { color: var(--el-color-danger); }
.meta-time { color: var(--color-text-tertiary); font-size: 12px; }

.history-args,
.history-result {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.history-label {
  font-size: 11px;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.history-args code {
  font-size: 12px;
  font-family: monospace;
  color: var(--color-text-secondary);
  word-break: break-all;
}
.result-pre {
  margin: 0;
  font-size: 12px;
  font-family: monospace;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--color-text-primary);
  max-height: 200px;
  overflow-y: auto;
  background: var(--color-bg-card);
  border-radius: 4px;
  padding: 8px 10px;
}
</style>
