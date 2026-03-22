<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="page-header-left">
        <div class="page-title">模型提供商管理</div>
        <div class="page-desc">管理接入的 AI 模型提供商，配置 API Key 和连接信息</div>
      </div>
      <div class="page-header-actions">
        <button class="btn-primary" @click="dialogRef?.open()">
          <el-icon><Plus /></el-icon>
          新增提供商
        </button>
      </div>
    </div>

    <!-- 列表 -->
    <div class="hify-card provider-card">
      <HifyTable
        :columns="columns"
        :api="fetchProviders"
        :row-style="{ height: '52px' }"
        ref="tableRef"
      >
        <!-- 状态列 -->
        <template #status="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>
        <!-- 操作列 -->
        <template #action="{ row }">
          <el-button type="primary" link size="small" @click="dialogRef?.open(row)">编辑</el-button>
          <el-button type="danger" link size="small" style="margin-left: 8px;" @click="onDelete(row)">删除</el-button>
        </template>
      </HifyTable>
    </div>

    <!-- 新增/编辑弹窗 -->
    <HifyFormDialog
      ref="dialogRef"
      :title="dialogTitle"
      :rules="rules"
      width="520px"
      label-width="100px"
      @submit="onSubmit"
    >
      <template #default="{ form }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入提供商名称" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择类型" style="width: 100%">
            <el-option v-for="t in providerTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" placeholder="请输入 API Key" show-password />
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://api.openai.com/v1" />
        </el-form-item>
      </template>
    </HifyFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import HifyTable from '@/components/base/HifyTable.vue'
import HifyFormDialog from '@/components/base/HifyFormDialog.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'
import type { TableColumn } from '@/components/base/HifyTable.vue'

interface Provider {
  id: number
  name: string
  type: string
  baseUrl: string
  apiKey: string
  enabled: boolean
  createdAt: string
}

// ── Mock 数据 ──────────────────────────────────────────────
let nextId = 6
const mockData = ref<Provider[]>([
  { id: 1, name: 'OpenAI 官方',  type: 'OpenAI',  baseUrl: 'https://api.openai.com',                     apiKey: 'sk-***',  enabled: true,  createdAt: '2024-01-10 10:00' },
  { id: 2, name: 'Claude 官方',  type: 'Claude',  baseUrl: 'https://api.anthropic.com',                  apiKey: 'sk-***',  enabled: true,  createdAt: '2024-01-12 14:30' },
  { id: 3, name: 'Gemini Pro',   type: 'Gemini',  baseUrl: 'https://generativelanguage.googleapis.com',  apiKey: 'AIza***', enabled: true,  createdAt: '2024-02-01 09:15' },
  { id: 4, name: '本地 Ollama',  type: 'Ollama',  baseUrl: 'http://localhost:11434',                     apiKey: '',        enabled: false, createdAt: '2024-02-20 16:00' },
  { id: 5, name: 'Azure OpenAI', type: 'OpenAI',  baseUrl: 'https://xxx.openai.azure.com',               apiKey: 'az-***',  enabled: true,  createdAt: '2024-03-05 11:45' },
])

const providerTypes = [
  { label: 'OpenAI', value: 'OpenAI' },
  { label: 'Claude', value: 'Claude' },
  { label: 'Gemini', value: 'Gemini' },
  { label: 'Ollama', value: 'Ollama' },
]

// ── 模拟 API ───────────────────────────────────────────────
const fetchProviders = async ({ page, pageSize }: { page: number; pageSize: number }) => {
  await new Promise(r => setTimeout(r, 300))
  const start = (page - 1) * pageSize
  return {
    list: mockData.value.slice(start, start + pageSize) as unknown as Record<string, unknown>[],
    total: mockData.value.length,
  }
}

// ── 表格列配置（narrow 时隐藏 baseUrl 和 createdAt）────────
const columns = computed<TableColumn[]>(() => [
  { label: '名称',     prop: 'name',      minWidth: 140 },
  { label: '类型',     prop: 'type',      width: 100 },
  { label: 'Base URL', prop: 'baseUrl',   minWidth: 200, hideOnNarrow: true },
  { label: '状态',     slot: 'status',    width: 90 },
  { label: '创建时间', prop: 'createdAt', width: 160,    hideOnNarrow: true },
  { label: '操作',     slot: 'action',    width: 130 },
])

// ── 弹窗 ───────────────────────────────────────────────────
const tableRef = ref<InstanceType<typeof HifyTable>>()
const dialogRef = ref<InstanceType<typeof HifyFormDialog>>()

const rules: FormRules = {
  name:    [{ required: true, message: '请输入名称',    trigger: 'blur' }],
  type:    [{ required: true, message: '请选择类型',    trigger: 'change' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
}

const dialogTitle = computed(() => '提供商信息')

const onSubmit = (data: Record<string, unknown>, mode: 'add' | 'edit') => {
  if (mode === 'add') {
    mockData.value.unshift({
      ...(data as Omit<Provider, 'id' | 'enabled' | 'createdAt'>),
      id: nextId++,
      enabled: true,
      createdAt: new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-'),
    } as Provider)
  } else {
    const idx = mockData.value.findIndex(p => p.id === data.id)
    if (idx !== -1) mockData.value[idx] = { ...mockData.value[idx], ...(data as unknown as Provider) }
  }
  notifySuccess(mode === 'add' ? '新增成功' : '保存成功')
  dialogRef.value?.close()
  tableRef.value?.refresh()
}

// ── 删除 ───────────────────────────────────────────────────
const { confirm } = useConfirm()

const onDelete = async (row: Provider) => {
  await confirm(
    `确定删除提供商「${row.name}」吗？`,
    async () => { mockData.value = mockData.value.filter(p => p.id !== row.id) },
    '删除成功'
  )
  tableRef.value?.refresh()
}
</script>

<style scoped>
/* 标题和卡片间距 */
.page-header { margin-bottom: 16px; }

/* 表头浅灰背景 */
.provider-card :deep(.el-table th.el-table__cell) {
  background-color: var(--color-bg-page);
}

/* 行 hover */
.provider-card :deep(.el-table__row:hover > td) {
  background-color: var(--color-bg-hover) !important;
}

/* 分页区加分割线 */
.provider-card :deep(.hify-table-pagination) {
  padding-top: 12px;
  border-top: 1px solid var(--color-border-default);
  justify-content: flex-end;
}
</style>
