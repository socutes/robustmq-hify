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
        <!-- 启用状态列 -->
        <template #status="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>
        <!-- 健康状态列 -->
        <template #health="{ row }">
          <template v-if="row.health">
            <el-tag :type="healthTagType(row.health.status)" size="small">
              {{ healthLabel(row.health.status) }}
            </el-tag>
            <span v-if="row.health.latencyMs != null" class="latency-ms">
              {{ row.health.latencyMs }}ms
            </span>
          </template>
          <el-tag v-else type="info" size="small">未知</el-tag>
        </template>
        <!-- 模型数列 -->
        <template #models="{ row }">
          <el-popover
            v-if="row.models && row.models.length > 0"
            placement="bottom-start"
            :width="300"
            trigger="click"
          >
            <template #reference>
              <span class="model-count-link">{{ enabledModelCount(row) }} 个</span>
            </template>
            <div class="model-list-popup">
              <div class="model-list-title">已配置模型（{{ row.models.length }} 个）</div>
              <div
                v-for="m in row.models"
                :key="m.id"
                class="model-list-item"
              >
                <span>{{ m.displayName || m.modelId }}</span>
                <el-tag :type="m.enabled ? 'success' : 'info'" size="small">
                  {{ m.enabled ? '启用' : '禁用' }}
                </el-tag>
              </div>
            </div>
          </el-popover>
          <span v-else class="text-muted">0 个</span>
        </template>
        <!-- 操作列 -->
        <template #action="{ row }">
          <el-button type="primary" link size="small" @click="dialogRef?.open(row)">编辑</el-button>
          <el-button
            type="warning" link size="small"
            style="margin-left: 4px;"
            :loading="testingId === row.id"
            @click="onTestConnection(row)"
          >测试</el-button>
          <el-button type="danger" link size="small" style="margin-left: 4px;" @click="onDelete(row)">删除</el-button>
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
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" type="password" placeholder="留空表示不修改" show-password />
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="可选" />
        </el-form-item>
      </template>
    </HifyFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import HifyTable from '@/components/base/HifyTable.vue'
import HifyFormDialog from '@/components/base/HifyFormDialog.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'
import type { TableColumn } from '@/components/base/HifyTable.vue'
import {
  getProviderList,
  createProvider,
  updateProvider,
  deleteProvider,
  testConnection,
} from '@/api/provider'
import type { ProviderVO, HealthStatus } from '@/api/provider'

const providerTypes = [
  { label: 'OpenAI',            value: 'OPENAI' },
  { label: 'Anthropic (Claude)', value: 'ANTHROPIC' },
  { label: 'Google Gemini',     value: 'GEMINI' },
  { label: 'Azure OpenAI',      value: 'AZURE_OPENAI' },
  { label: 'Ollama',            value: 'OLLAMA' },
  { label: 'OpenAI Compatible', value: 'OPENAI_COMPATIBLE' },
]

// ── API ────────────────────────────────────────────────────
const fetchProviders = async ({ page, pageSize }: { page: number; pageSize: number }) => {
  const res = await getProviderList({ page, pageSize })
  return { list: res.list as unknown as Record<string, unknown>[], total: res.total }
}

// ── 健康状态工具 ───────────────────────────────────────────
const healthTagType = (status: HealthStatus) => {
  const map: Record<HealthStatus, 'success' | 'danger' | 'warning' | 'info'> = {
    UP: 'success', DOWN: 'danger', DEGRADED: 'warning', UNKNOWN: 'info',
  }
  return map[status] ?? 'info'
}
const healthLabel = (status: HealthStatus) => {
  const map: Record<HealthStatus, string> = {
    UP: '正常', DOWN: '故障', DEGRADED: '降级', UNKNOWN: '未知',
  }
  return map[status] ?? status
}
const enabledModelCount = (row: ProviderVO) =>
  row.models?.filter(m => m.enabled).length ?? 0

// ── 表格列配置 ─────────────────────────────────────────────
const columns = computed<TableColumn[]>(() => [
  { label: '名称',     prop: 'name',     minWidth: 140 },
  { label: '类型',     prop: 'type',     width: 140 },
  { label: 'Base URL', prop: 'baseUrl',  minWidth: 200, hideOnNarrow: true },
  { label: '状态',     slot: 'status',   width: 80 },
  { label: '健康',     slot: 'health',   width: 120 },
  { label: '模型数',   slot: 'models',   width: 80 },
  { label: '操作',     slot: 'action',   width: 160 },
])

// ── 弹窗 ───────────────────────────────────────────────────
const tableRef = ref<InstanceType<typeof HifyTable>>()
const dialogRef = ref<InstanceType<typeof HifyFormDialog>>()

const rules: FormRules = {
  name:    [{ required: true, message: '请输入名称',      trigger: 'blur' }],
  type:    [{ required: true, message: '请选择类型',      trigger: 'change' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
}

const dialogTitle = computed(() => '提供商信息')

const onSubmit = async (data: Record<string, unknown>, mode: 'add' | 'edit') => {
  const apiKey = (data.apiKey as string) || ''
  if (mode === 'add') {
    await createProvider({
      name: data.name as string,
      type: data.type as any,
      baseUrl: data.baseUrl as string,
      description: data.description as string | undefined,
      authConfig: apiKey ? { api_key: apiKey } : {},
    })
  } else {
    const updateData: any = {
      name: data.name,
      baseUrl: data.baseUrl,
      description: data.description,
    }
    if (apiKey) updateData.authConfig = { api_key: apiKey }
    await updateProvider(data.id as number, updateData)
  }
  notifySuccess(mode === 'add' ? '新增成功' : '保存成功')
  dialogRef.value?.close()
  tableRef.value?.refresh()
}

// ── 删除 ───────────────────────────────────────────────────
const { confirm } = useConfirm()

const onDelete = async (row: ProviderVO) => {
  await confirm(
    `确定删除提供商「${row.name}」吗？`,
    async () => { await deleteProvider(row.id) },
    '删除成功'
  )
  tableRef.value?.refresh()
}

// ── 连通性测试 ─────────────────────────────────────────────
const testingId = ref<number | null>(null)

const onTestConnection = async (row: ProviderVO) => {
  testingId.value = row.id
  try {
    const result = await testConnection(row.id)
    if (result.success) {
      ElMessage.success(`连接成功，延迟 ${result.latencyMs}ms，发现 ${result.modelCount} 个模型`)
    } else {
      ElMessage.error(`连接失败：${result.errorMessage}`)
    }
  } finally {
    testingId.value = null
  }
}
</script>

<style scoped>
.page-header { margin-bottom: 16px; }

.provider-card :deep(.el-table th.el-table__cell) {
  background-color: var(--color-bg-page);
}
.provider-card :deep(.el-table__row:hover > td) {
  background-color: var(--color-bg-hover) !important;
}
.provider-card :deep(.hify-table-pagination) {
  padding-top: 12px;
  border-top: 1px solid var(--color-border-default);
  justify-content: flex-end;
}

.latency-ms {
  margin-left: 6px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.model-count-link {
  color: var(--color-primary);
  cursor: pointer;
  font-size: 13px;
}
.model-count-link:hover { text-decoration: underline; }

.text-muted {
  color: var(--color-text-tertiary);
  font-size: 13px;
}

.model-list-popup { padding: 4px 0; }
.model-list-title {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--color-border-default);
}
.model-list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 13px;
}
</style>
