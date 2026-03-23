<template>
  <div class="page-container">
    <div class="page-header">
      <div class="page-header-left">
        <div class="page-title">Agent 管理</div>
        <div class="page-desc">创建和配置 AI Agent，绑定模型、工具与 System Prompt</div>
      </div>
      <div class="page-header-actions">
        <button class="btn-primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          新增 Agent
        </button>
      </div>
    </div>

    <div class="hify-card agent-card">
      <HifyTable
        ref="tableRef"
        :columns="columns"
        :api="fetchAgents"
        :row-style="{ height: '52px' }"
      >
        <template #modelName="{ row }">
          <span class="model-label">{{ modelNameMap[row.modelConfigId] ?? '—' }}</span>
        </template>
        <template #temperature="{ row }">
          <span class="mono">{{ row.temperature.toFixed(1) }}</span>
        </template>
        <template #enabled="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
            {{ row.enabled === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
        <template #action="{ row }">
          <el-button type="primary" link size="small" @click="openEdit(row)">编辑</el-button>
          <el-button type="danger" link size="small" style="margin-left:4px" @click="onDelete(row)">删除</el-button>
        </template>
      </HifyTable>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'add' ? '新增 Agent' : '编辑 Agent'"
      width="660px"
      :close-on-click-modal="false"
      destroy-on-close
      @closed="resetForm"
    >
      <el-tabs v-model="activeTab">
        <!-- ── 基本信息 tab ── -->
        <el-tab-pane label="基本配置" name="basic">
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-width="110px"
            label-position="right"
            style="margin-top:12px"
            @submit.prevent
          >
            <el-form-item label="名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入 Agent 名称" />
            </el-form-item>

            <el-form-item label="描述">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="2"
                placeholder="可选，简述 Agent 的用途"
              />
            </el-form-item>

            <el-form-item label="模型" prop="modelConfigId">
              <el-select
                v-model="form.modelConfigId"
                placeholder="请选择模型"
                style="width:100%"
                :loading="modelsLoading"
              >
                <el-option-group
                  v-for="group in modelGroups"
                  :key="group.providerName"
                  :label="group.providerName"
                >
                  <el-option
                    v-for="m in group.models"
                    :key="m.modelConfigId"
                    :label="m.modelName"
                    :value="m.modelConfigId"
                  />
                </el-option-group>
              </el-select>
            </el-form-item>

            <el-form-item label="System Prompt">
              <el-input
                v-model="form.systemPrompt"
                type="textarea"
                :rows="6"
                placeholder="定义 Agent 的角色、行为和约束..."
                style="font-family: monospace; font-size: 13px"
              />
            </el-form-item>

            <el-form-item label="Temperature">
              <div class="slider-row">
                <el-slider
                  v-model="form.temperature"
                  :min="0" :max="1" :step="0.1"
                  :marks="{ 0: '0', 0.5: '0.5', 1: '1' }"
                  style="flex:1"
                />
                <span class="slider-val">{{ form.temperature.toFixed(1) }}</span>
              </div>
            </el-form-item>

            <el-form-item label="最大输出 Token">
              <el-input-number
                v-model="form.maxTokens"
                :min="1" :max="32768" :step="256"
                style="width:180px"
              />
            </el-form-item>

            <el-form-item label="上下文轮数">
              <el-input-number
                v-model="form.maxContextTurns"
                :min="1" :max="100"
                style="width:180px"
              />
              <span class="form-hint">保留最近 N 轮对话历史</span>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- ── 工具绑定 tab ── -->
        <el-tab-pane label="工具绑定" name="tools">
          <div class="tools-pane">
            <div v-if="mcpServers.length === 0" class="tools-empty">
              暂无可用的 MCP Server，请先在工具管理中添加
            </div>
            <el-checkbox-group v-else v-model="form.toolIds" class="tools-checkbox-group">
              <el-checkbox
                v-for="s in mcpServers"
                :key="s.id"
                :value="s.id"
                class="tool-checkbox-item"
              >
                <div class="tool-info">
                  <span class="tool-name">{{ s.name }}</span>
                  <span class="tool-endpoint">{{ s.endpoint }}</span>
                </div>
              </el-checkbox>
            </el-checkbox-group>
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">
          {{ dialogMode === 'add' ? '确认' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import HifyTable from '@/components/base/HifyTable.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'
import type { TableColumn } from '@/components/base/HifyTable.vue'
import {
  getAgentList, getAgentDetail, createAgent, updateAgent, bindAgentTools, deleteAgent,
  getModelOptions,
} from '@/api/agent'
import type { AgentListItem, ModelOption } from '@/api/agent'

// ── MCP Server 类型（暂无后端接口，用空列表占位）────────────────
interface McpServer { id: number; name: string; endpoint: string }
const mcpServers = ref<McpServer[]>([])

// ── 模型选项 ───────────────────────────────────────────────
const modelOptions = ref<ModelOption[]>([])
const modelsLoading = ref(false)
// 按供应商分组
const modelGroups = computed(() => {
  const map = new Map<string, { providerName: string; models: ModelOption[] }>()
  for (const m of modelOptions.value) {
    if (!map.has(m.providerName)) map.set(m.providerName, { providerName: m.providerName, models: [] })
    map.get(m.providerName)!.models.push(m)
  }
  return Array.from(map.values())
})
// modelConfigId → 模型名称，用于列表展示
const modelNameMap = computed(() => {
  const m: Record<number, string> = {}
  for (const opt of modelOptions.value) m[opt.modelConfigId] = opt.modelName
  return m
})

onMounted(async () => {
  modelsLoading.value = true
  try { modelOptions.value = await getModelOptions() } finally { modelsLoading.value = false }
})

// ── 表格 ───────────────────────────────────────────────────
const tableRef = ref<InstanceType<typeof HifyTable>>()

const columns = computed<TableColumn[]>(() => [
  { label: '名称',     prop: 'name',        minWidth: 140 },
  { label: '关联模型', slot: 'modelName',   width: 140 },
  { label: '工具数',   prop: 'toolCount',   width: 80 },
  { label: 'Temperature', slot: 'temperature', width: 110 },
  { label: '状态',     slot: 'enabled',     width: 80 },
  { label: '创建时间', prop: 'createdAt',   width: 160, hideOnNarrow: true },
  { label: '操作',     slot: 'action',      width: 120 },
])

const fetchAgents = async ({ page, pageSize }: { page: number; pageSize: number }) => {
  const res = await getAgentList({ page, pageSize })
  return { list: res.list as unknown as Record<string, unknown>[], total: res.total }
}

// ── 表单状态 ───────────────────────────────────────────────
const dialogVisible = ref(false)
const dialogMode = ref<'add' | 'edit'>('add')
const activeTab = ref('basic')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const defaultForm = () => ({
  name: '',
  description: '',
  systemPrompt: '',
  modelConfigId: null as number | null,
  temperature: 0.7,
  maxTokens: 2048,
  maxContextTurns: 10,
  toolIds: [] as number[],
})

const form = ref(defaultForm())

const rules: FormRules = {
  name:          [{ required: true, message: '请输入名称', trigger: 'blur' }],
  modelConfigId: [{ required: true, message: '请选择模型', trigger: 'change' }],
}

const resetForm = () => {
  form.value = defaultForm()
  activeTab.value = 'basic'
  editingId.value = null
  formRef.value?.clearValidate()
}

const openCreate = () => {
  dialogMode.value = 'add'
  dialogVisible.value = true
}

const openEdit = async (row: AgentListItem) => {
  dialogMode.value = 'edit'
  editingId.value = row.id
  dialogVisible.value = true
  try {
    const detail = await getAgentDetail(row.id)
    form.value = {
      name: detail.name,
      description: detail.description ?? '',
      systemPrompt: detail.systemPrompt ?? '',
      modelConfigId: detail.modelConfigId,
      temperature: detail.temperature,
      maxTokens: detail.maxTokens,
      maxContextTurns: detail.maxContextTurns,
      toolIds: detail.toolIds ?? [],
    }
  } catch {
    ElMessage.error('加载 Agent 详情失败')
    dialogVisible.value = false
  }
}

const onSubmit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (dialogMode.value === 'add') {
      await createAgent({
        name: form.value.name,
        description: form.value.description,
        systemPrompt: form.value.systemPrompt,
        modelConfigId: form.value.modelConfigId!,
        temperature: form.value.temperature,
        maxTokens: form.value.maxTokens,
        maxContextTurns: form.value.maxContextTurns,
        toolIds: form.value.toolIds,
      })
      notifySuccess('新增成功')
    } else {
      await updateAgent(editingId.value!, {
        name: form.value.name,
        description: form.value.description,
        systemPrompt: form.value.systemPrompt,
        modelConfigId: form.value.modelConfigId!,
        temperature: form.value.temperature,
        maxTokens: form.value.maxTokens,
        maxContextTurns: form.value.maxContextTurns,
      })
      await bindAgentTools(editingId.value!, form.value.toolIds)
      notifySuccess('保存成功')
    }
    dialogVisible.value = false
    tableRef.value?.refresh()
  } finally {
    submitting.value = false
  }
}

// ── 删除 ───────────────────────────────────────────────────
const { confirm } = useConfirm()

const onDelete = async (row: AgentListItem) => {
  await confirm(
    `确定删除 Agent「${row.name}」吗？`,
    async () => { await deleteAgent(row.id) },
    '删除成功'
  )
  tableRef.value?.refresh()
}
</script>

<style scoped>
.page-header { margin-bottom: 16px; }

.agent-card :deep(.el-table th.el-table__cell) {
  background-color: var(--color-bg-page);
}
.agent-card :deep(.el-table__row:hover > td) {
  background-color: var(--color-bg-hover) !important;
}
.agent-card :deep(.hify-table-pagination) {
  padding-top: 12px;
  border-top: 1px solid var(--color-border-default);
  justify-content: flex-end;
}

.model-label { font-size: 13px; }
.mono { font-family: monospace; font-size: 13px; }

/* Slider 行 */
.slider-row {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
}
.slider-val {
  font-family: monospace;
  font-size: 13px;
  color: var(--color-text-primary);
  min-width: 28px;
  text-align: right;
}

/* form hint */
.form-hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--color-text-tertiary);
}

/* 工具绑定区 */
.tools-pane {
  padding: 16px 4px;
  min-height: 160px;
}
.tools-empty {
  color: var(--color-text-tertiary);
  font-size: 13px;
  text-align: center;
  padding: 40px 0;
}
.tools-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.tool-checkbox-item {
  height: auto;
  align-items: flex-start;
}
.tool-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.4;
}
.tool-name {
  font-size: 13px;
  color: var(--color-text-primary);
}
.tool-endpoint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  font-family: monospace;
}
</style>
