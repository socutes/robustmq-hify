<template>
  <div class="workflow-list">
    <div class="page-header">
      <div>
        <h2 class="page-title">工作流</h2>
        <p class="page-desc">管理智能客服分类等工作流配置</p>
      </div>
      <el-button type="primary" @click="$router.push('/workflows/create')">
        <el-icon><Plus /></el-icon>新建工作流
      </el-button>
    </div>

    <el-table :data="workflows" v-loading="loading" class="workflow-table" stripe>
      <el-table-column prop="name" label="工作流名称" min-width="200">
        <template #default="{ row }">
          <div class="wf-name">
            <el-icon class="wf-icon"><Share /></el-icon>
            <span>{{ row.name }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.status === 'PUBLISHED' ? 'success' : row.status === 'DISABLED' ? 'danger' : 'info'" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="viewDetail(row)">查看</el-button>
          <el-popconfirm title="确认删除这个工作流？" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="工作流详情" size="600px" direction="rtl">
      <div v-if="detail" class="detail-panel">
        <div class="detail-meta">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="detail.status === 'PUBLISHED' ? 'success' : 'info'" size="small">
                {{ statusLabel(detail.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">{{ detail.description || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <h4>节点 ({{ detail.nodes.length }})</h4>
          <div v-for="node in detail.nodes" :key="node.nodeKey" class="node-card">
            <div class="node-header">
              <el-tag :type="nodeTypeColor(node.type)" size="small" effect="dark">{{ node.type }}</el-tag>
              <span class="node-key">{{ node.nodeKey }}</span>
              <span class="node-name">{{ node.name }}</span>
            </div>
            <div v-if="node.config && Object.keys(node.config).length" class="node-config">
              <pre>{{ JSON.stringify(node.config, null, 2) }}</pre>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h4>连线 ({{ detail.edges.length }})</h4>
          <div v-for="(edge, i) in detail.edges" :key="i" class="edge-row">
            <span class="edge-source">{{ edge.sourceNodeKey }}</span>
            <el-icon><Right /></el-icon>
            <span class="edge-target">{{ edge.targetNodeKey }}</span>
            <el-tag v-if="edge.condition" size="small" type="warning">{{ edge.condition }}</el-tag>
            <el-tag v-else size="small" type="info">无条件</el-tag>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Share, Right } from '@element-plus/icons-vue'
import { listWorkflows, getWorkflow, deleteWorkflow, type WorkflowListItem, type WorkflowDetail } from '@/api/workflow'

const loading = ref(false)
const workflows = ref<WorkflowListItem[]>([])
const drawerVisible = ref(false)
const detail = ref<WorkflowDetail | null>(null)

async function fetchList() {
  loading.value = true
  try {
    const res = await listWorkflows() as any
    workflows.value = res?.list || []
  } finally {
    loading.value = false
  }
}

async function viewDetail(row: WorkflowListItem) {
  const res = await getWorkflow(row.id) as any
  detail.value = res
  drawerVisible.value = true
}

async function handleDelete(id: number) {
  await deleteWorkflow(id)
  ElMessage.success('已删除')
  fetchList()
}

function statusLabel(s: string) {
  return { DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已禁用' }[s] || s
}

function nodeTypeColor(type: string) {
  const map: Record<string, string> = {
    START: 'success', END: 'danger', LLM: 'primary', CONDITION: 'warning',
    KNOWLEDGE: '', API_CALL: 'info'
  }
  return map[type] || ''
}

function formatTime(t: string) {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 16)
}

onMounted(fetchList)
</script>

<style scoped>
.workflow-list { padding: 0; }

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}
.page-title { margin: 0 0 4px; font-size: 18px; font-weight: 600; color: var(--el-text-color-primary); }
.page-desc { margin: 0; font-size: 13px; color: var(--el-text-color-secondary); }

.workflow-table { width: 100%; }

.wf-name { display: flex; align-items: center; gap: 8px; }
.wf-icon { color: var(--el-color-primary); }

.detail-section { margin-top: 20px; }
.detail-section h4 { margin: 0 0 12px; font-size: 14px; font-weight: 600; color: var(--el-text-color-primary); }

.node-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 8px;
  background: var(--el-fill-color-extra-light);
}
.node-header { display: flex; align-items: center; gap: 8px; }
.node-key { font-family: monospace; font-size: 12px; color: var(--el-text-color-secondary); }
.node-name { font-size: 13px; color: var(--el-text-color-primary); }
.node-config {
  margin-top: 8px;
  padding: 8px;
  background: var(--el-fill-color);
  border-radius: 4px;
  overflow: auto;
  max-height: 120px;
}
.node-config pre { margin: 0; font-size: 11px; line-height: 1.5; color: var(--el-text-color-regular); }

.edge-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  font-size: 13px;
}
.edge-source { font-family: monospace; color: var(--el-color-primary); }
.edge-target { font-family: monospace; color: var(--el-color-success); }

.detail-meta { margin-bottom: 16px; }
</style>
