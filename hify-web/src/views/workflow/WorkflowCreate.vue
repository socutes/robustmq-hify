<template>
  <div class="workflow-create">
    <div class="page-header">
      <div>
        <h2 class="page-title">新建工作流</h2>
        <p class="page-desc">通过 JSON 配置定义节点和连线</p>
      </div>
      <el-button @click="$router.push('/workflows')">返回列表</el-button>
    </div>

    <el-form :model="form" label-width="100px" class="create-form">
      <el-form-item label="工作流名称" required>
        <el-input v-model="form.name" placeholder="如：智能客服分类工作流" maxlength="100" show-word-limit />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" placeholder="可选" maxlength="500" />
      </el-form-item>
      <el-form-item label="工作流配置" required>
        <div style="width: 100%">
          <div class="json-toolbar">
            <span class="json-label">JSON 格式 — 包含 nodes 和 edges 数组</span>
            <el-button size="small" @click="formatJson">格式化</el-button>
            <el-button size="small" @click="resetExample">还原示例</el-button>
          </div>
          <el-input
            v-model="jsonStr"
            type="textarea"
            :rows="24"
            placeholder="输入工作流配置 JSON..."
            class="json-editor"
            :class="{ 'json-error': jsonError }"
            spellcheck="false"
          />
          <div v-if="jsonError" class="json-error-msg">JSON 格式错误：{{ jsonError }}</div>
        </div>
      </el-form-item>
    </el-form>

    <div class="form-actions">
      <el-button @click="$router.push('/workflows')">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">创建工作流</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createWorkflow } from '@/api/workflow'

const router = useRouter()
const submitting = ref(false)
const jsonError = ref('')

const EXAMPLE = {
  nodes: [
    { nodeKey: 'start', type: 'START', name: '开始', config: {} },
    {
      nodeKey: 'classify', type: 'LLM', name: '问题分类',
      config: {
        prompt: '你是意图分类器，用户消息：{{start.userMessage}}，仅回复：售前、售后 或 技术支持',
        outputVariable: 'intent'
      }
    },
    {
      nodeKey: 'router', type: 'CONDITION', name: '路由分发',
      config: { expression: '{{classify.intent}}', outputVariable: 'route' }
    },
    {
      nodeKey: 'presale', type: 'LLM', name: '售前咨询',
      config: { prompt: '你是售前顾问，解答产品功能和优势。用户问题：{{start.userMessage}}', outputVariable: 'answer' }
    },
    {
      nodeKey: 'aftersale', type: 'LLM', name: '售后服务',
      config: { prompt: '你是售后客服，解答退换货和保修问题。用户问题：{{start.userMessage}}', outputVariable: 'answer' }
    },
    {
      nodeKey: 'techsupport', type: 'LLM', name: '技术支持',
      config: { prompt: '你是技术工程师，帮用户排查使用问题。用户问题：{{start.userMessage}}', outputVariable: 'answer' }
    },
    { nodeKey: 'end', type: 'END', name: '结束', config: { outputVariable: 'answer' } }
  ],
  edges: [
    { sourceNodeKey: 'start', targetNodeKey: 'classify', condition: null },
    { sourceNodeKey: 'classify', targetNodeKey: 'router', condition: null },
    { sourceNodeKey: 'router', targetNodeKey: 'presale', condition: '售前' },
    { sourceNodeKey: 'router', targetNodeKey: 'aftersale', condition: '售后' },
    { sourceNodeKey: 'router', targetNodeKey: 'techsupport', condition: '技术支持' },
    { sourceNodeKey: 'presale', targetNodeKey: 'end', condition: null },
    { sourceNodeKey: 'aftersale', targetNodeKey: 'end', condition: null },
    { sourceNodeKey: 'techsupport', targetNodeKey: 'end', condition: null }
  ]
}

const form = ref({ name: '智能客服分类工作流', description: '根据用户意图分发到售前/售后/技术支持路径' })
const jsonStr = ref(JSON.stringify(EXAMPLE, null, 2))

function formatJson() {
  try {
    const parsed = JSON.parse(jsonStr.value)
    jsonStr.value = JSON.stringify(parsed, null, 2)
    jsonError.value = ''
  } catch (e: any) {
    jsonError.value = e.message
  }
}

function resetExample() {
  jsonStr.value = JSON.stringify(EXAMPLE, null, 2)
  jsonError.value = ''
}

function validateJson(): any | null {
  try {
    const parsed = JSON.parse(jsonStr.value)
    jsonError.value = ''
    return parsed
  } catch (e: any) {
    jsonError.value = e.message
    return null
  }
}

async function handleSubmit() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入工作流名称')
    return
  }
  const parsed = validateJson()
  if (!parsed) {
    ElMessage.error('JSON 格式错误，请修正后再提交')
    return
  }
  if (!parsed.nodes || !Array.isArray(parsed.nodes)) {
    ElMessage.error('JSON 中缺少 nodes 数组')
    return
  }

  submitting.value = true
  try {
    await createWorkflow({
      name: form.value.name,
      description: form.value.description,
      nodes: parsed.nodes,
      edges: parsed.edges || []
    })
    ElMessage.success('工作流创建成功')
    router.push('/workflows')
  } catch (e: any) {
    ElMessage.error(e?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.workflow-create { padding: 0; max-width: 900px; }

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}
.page-title { margin: 0 0 4px; font-size: 18px; font-weight: 600; color: var(--el-text-color-primary); }
.page-desc { margin: 0; font-size: 13px; color: var(--el-text-color-secondary); }

.create-form { background: var(--el-bg-color); padding: 24px; border-radius: 8px; border: 1px solid var(--el-border-color-light); }

.json-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.json-label { font-size: 12px; color: var(--el-text-color-secondary); flex: 1; }

.json-editor :deep(textarea) {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.6;
  background: #1a1b26;
  color: #a9b1d6;
  border-color: var(--el-border-color);
}
.json-editor.json-error :deep(textarea) { border-color: var(--el-color-danger); }
.json-error-msg { margin-top: 4px; font-size: 12px; color: var(--el-color-danger); }

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 20px;
}
</style>
