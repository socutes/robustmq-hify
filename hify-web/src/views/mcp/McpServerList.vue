<template>
  <div class="page-container">
    <div class="page-header">
      <div class="page-header-left">
        <div class="page-title">MCP 工具服务</div>
        <div class="page-desc">管理 MCP Server，让 Agent 能调用外部系统（订单、物流、工单等）</div>
      </div>
      <div class="page-header-actions">
        <button class="btn-primary" @click="dialogRef?.open()">
          <el-icon><Plus /></el-icon>
          新增 Server
        </button>
      </div>
    </div>

    <div class="hify-card">
      <HifyTable
        :columns="columns"
        :api="fetchServers"
        ref="tableRef"
      >
        <!-- 状态 -->
        <template #status="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>

        <!-- endpoint -->
        <template #endpoint="{ row }">
          <span class="endpoint-text" :title="row.endpoint">{{ row.endpoint }}</span>
        </template>

        <!-- 操作 -->
        <template #action="{ row }">
          <el-button type="primary" link size="small" @click="dialogRef?.open(row)">编辑</el-button>
          <el-button
            type="warning" link size="small"
            style="margin-left: 4px;"
            :loading="testingId === row.id"
            @click="onTest(row)"
          >测试</el-button>
          <el-button
            type="success" link size="small"
            style="margin-left: 4px;"
            @click="router.push(`/mcp/${row.id}/debug`)"
          >调试</el-button>
          <el-button
            type="danger" link size="small"
            style="margin-left: 4px;"
            @click="onDelete(row)"
          >删除</el-button>
        </template>
      </HifyTable>
    </div>

    <!-- 测试结果抽屉 -->
    <el-drawer v-model="testDrawerVisible" title="连通测试结果" size="400px" direction="rtl">
      <template v-if="testResult">
        <div v-if="testResult.success" class="test-success">
          <el-icon class="test-icon success"><CircleCheck /></el-icon>
          <div class="test-meta">连接成功，延迟 {{ testResult.latencyMs }}ms</div>
          <div class="tool-list-title">发现 {{ testResult.tools?.length ?? 0 }} 个工具</div>
          <div v-if="testResult.tools?.length" class="tool-list">
            <div v-for="t in testResult.tools" :key="t" class="tool-item">
              <el-icon><Tools /></el-icon>
              <span>{{ t }}</span>
            </div>
          </div>
          <div v-else class="no-tools">该 Server 暂未声明工具</div>
        </div>
        <div v-else class="test-fail">
          <el-icon class="test-icon fail"><CircleClose /></el-icon>
          <div class="test-meta">连接失败</div>
          <div class="error-msg">{{ testResult.errorMessage }}</div>
        </div>
      </template>
    </el-drawer>

    <!-- 新增/编辑弹窗 -->
    <HifyFormDialog
      ref="dialogRef"
      title="MCP Server 信息"
      :rules="rules"
      width="500px"
      label-width="90px"
      @submit="onSubmit"
    >
      <template #default="{ form }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：订单服务" />
        </el-form-item>
        <el-form-item label="Endpoint" prop="endpoint">
          <el-input v-model="form.endpoint" placeholder="http://localhost:9001/mcp" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="可选" />
        </el-form-item>
        <el-form-item v-if="form.id" label="状态">
          <el-switch
            v-model="form.enabledBool"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </template>
    </HifyFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, CircleCheck, CircleClose, Tools } from '@element-plus/icons-vue'
import type { FormRules } from 'element-plus'
import HifyTable from '@/components/base/HifyTable.vue'
import HifyFormDialog from '@/components/base/HifyFormDialog.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'
import type { TableColumn } from '@/components/base/HifyTable.vue'
import {
  getMcpServerList,
  createMcpServer,
  updateMcpServer,
  deleteMcpServer,
  testMcpServer,
} from '@/api/mcp'
import type { McpServerVO, McpTestResult } from '@/api/mcp'

const router = useRouter()

// ── 表格 ───────────────────────────────────────────────────
const tableRef = ref<InstanceType<typeof HifyTable>>()

const columns = computed<TableColumn[]>(() => [
  { label: '名称',     prop: 'name',     minWidth: 140 },
  { label: 'Endpoint', slot: 'endpoint', minWidth: 220 },
  { label: '描述',     prop: 'description', minWidth: 160, hideOnNarrow: true },
  { label: '状态',     slot: 'status',   width: 80 },
  { label: '操作',     slot: 'action',   width: 180 },
])

const fetchServers = async ({ page, pageSize }: { page: number; pageSize: number }) => {
  const res = await getMcpServerList({ page, pageSize })
  return { list: res.list as unknown as Record<string, unknown>[], total: res.total }
}

// ── 弹窗 ───────────────────────────────────────────────────
const dialogRef = ref<InstanceType<typeof HifyFormDialog>>()

const rules: FormRules = {
  name:     [{ required: true, message: '请输入名称',     trigger: 'blur' }],
  endpoint: [{ required: true, message: '请输入 Endpoint', trigger: 'blur' }],
}

const onSubmit = async (data: Record<string, unknown>, mode: 'add' | 'edit') => {
  if (mode === 'add') {
    await createMcpServer({
      name: data.name as string,
      endpoint: data.endpoint as string,
      description: data.description as string | undefined,
    })
  } else {
    await updateMcpServer(data.id as number, {
      name: data.name as string,
      endpoint: data.endpoint as string,
      description: data.description as string | undefined,
      enabled: (data.enabledBool as boolean) ? 1 : 0,
    })
  }
  notifySuccess(mode === 'add' ? '新增成功' : '保存成功')
  dialogRef.value?.close()
  tableRef.value?.refresh()
}

// ── 删除 ───────────────────────────────────────────────────
const { confirm } = useConfirm()

const onDelete = async (row: McpServerVO) => {
  await confirm(
    `确定删除「${row.name}」吗？`,
    async () => { await deleteMcpServer(row.id) },
    '删除成功'
  )
  tableRef.value?.refresh()
}

// ── 连通测试 ────────────────────────────────────────────────
const testingId = ref<number | null>(null)
const testDrawerVisible = ref(false)
const testResult = ref<McpTestResult | null>(null)

const onTest = async (row: McpServerVO) => {
  testingId.value = row.id
  try {
    const result = await testMcpServer(row.id)
    testResult.value = result
    testDrawerVisible.value = true
  } finally {
    testingId.value = null
  }
}
</script>

<style scoped>
.endpoint-text {
  font-size: 12px;
  color: var(--color-text-secondary);
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
  max-width: 280px;
}

/* ── 测试结果 ─────────────────────────────────────────────── */
.test-success,
.test-fail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 24px 0;
}

.test-icon {
  font-size: 48px;
}
.test-icon.success { color: var(--el-color-success); }
.test-icon.fail    { color: var(--el-color-danger); }

.test-meta {
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text-primary);
}

.tool-list-title {
  font-size: 13px;
  color: var(--color-text-secondary);
  align-self: flex-start;
  width: 100%;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--color-border-default);
}

.tool-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tool-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 6px;
  background: var(--color-bg-page);
  font-size: 13px;
  color: var(--color-text-primary);
}

.tool-item .el-icon {
  color: var(--color-primary);
}

.no-tools {
  font-size: 13px;
  color: var(--color-text-tertiary);
}

.error-msg {
  font-size: 13px;
  color: var(--el-color-danger);
  text-align: center;
  padding: 0 16px;
  word-break: break-all;
}
</style>
