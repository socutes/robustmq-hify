<template>
  <div class="page-container">
    <!-- 顶部 -->
    <div class="page-header">
      <div class="page-header-left">
        <el-button link @click="router.push('/knowledge')" style="margin-right:8px;padding:0">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <div>
          <div class="page-title">{{ kbName || '知识库文档' }}</div>
          <div class="page-desc">管理文档，上传后自动分块向量化（当前为 Mock 模式）</div>
        </div>
      </div>
      <div class="page-header-actions">
        <el-button type="primary" :icon="Upload" @click="uploadVisible = true">上传文档</el-button>
      </div>
    </div>

    <!-- 文档表格 -->
    <div class="hify-card">
      <el-table :data="docList" style="width:100%" :row-style="{ height: '52px' }">
        <el-table-column label="文件名" min-width="200">
          <template #default="{ row }">
            <div class="doc-name">
              <el-icon class="doc-icon"><Document /></el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.fileType.toUpperCase() }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="分块数" width="90">
          <template #default="{ row }">
            <span v-if="row.status === 'DONE'" class="chunk-count">{{ row.chunkCount }}</span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <div class="status-cell">
              <el-tag :type="statusType(row.status)" size="small">
                <el-icon v-if="row.status === 'PROCESSING'" class="rotating"><Loading /></el-icon>
                {{ statusLabel(row.status) }}
              </el-tag>
              <el-tooltip v-if="row.status === 'FAILED' && row.errorMessage" :content="row.errorMessage" placement="top">
                <el-icon class="error-icon"><Warning /></el-icon>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary" link size="small"
              :disabled="row.status !== 'DONE'"
              @click="viewChunks(row)"
            >查看分块</el-button>
            <el-button
              type="danger" link size="small"
              :disabled="row.status === 'PROCESSING'"
              style="margin-left:4px"
              @click="onDelete(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="docList.length === 0" class="table-empty">
        <el-empty description="暂无文档，点击「上传文档」开始" />
      </div>
    </div>

    <!-- 上传弹窗 -->
    <el-dialog v-model="uploadVisible" title="上传文档" width="460px" :close-on-click-modal="false">
      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="false"
        :limit="5"
        accept=".txt,.md,.pdf"
        :before-upload="beforeUpload"
        :on-change="onFileChange"
        :file-list="fileList"
      >
        <el-icon class="el-icon--upload" :size="48"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
        <template #tip>
          <div class="upload-tip">支持 TXT / MD / PDF，单文件最大 10MB</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="fileList.length === 0" @click="doUpload">
          开始上传
        </el-button>
      </template>
    </el-dialog>

    <!-- 分块预览弹窗 -->
    <el-dialog v-model="chunksVisible" :title="`分块预览 — ${viewingDoc?.name}`" width="680px">
      <div v-if="chunksLoading" style="text-align:center;padding:30px">
        <el-icon class="rotating" :size="28"><Loading /></el-icon>
      </div>
      <div v-else class="chunks-list">
        <div v-for="chunk in chunks" :key="chunk.id" class="chunk-item">
          <div class="chunk-header">
            <span class="chunk-index">#{{ chunk.chunkIndex + 1 }}</span>
            <span class="chunk-tokens">{{ chunk.tokenCount }} tokens</span>
          </div>
          <div class="chunk-content" :class="{ expanded: expandedChunks.has(chunk.id) }">
            {{ chunk.content }}
          </div>
          <el-button
            v-if="chunk.content.length > 200"
            link size="small"
            @click="toggleChunk(chunk.id)"
          >
            {{ expandedChunks.has(chunk.id) ? '收起' : '展开全文' }}
          </el-button>
        </div>
        <el-empty v-if="chunks.length === 0" description="暂无分块" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadInstance, UploadRawFile, UploadFile } from 'element-plus'
import { ArrowLeft, Upload, Document, Loading, Warning, UploadFilled } from '@element-plus/icons-vue'
import {
  listDocuments, getDocument, deleteDocument,
  getChunks, uploadDocument, getKb,
} from '@/api/knowledge'
import type { KnowledgeDocument, ChunkVO } from '@/api/knowledge'

const route = useRoute()
const router = useRouter()
const kbId = Number(route.params.kbId)

const kbName = ref('')
const docList = ref<KnowledgeDocument[]>([])
const uploadVisible = ref(false)
const uploading = ref(false)
const fileList = ref<UploadFile[]>([])
const uploadRef = ref<UploadInstance>()

// 分块预览
const chunksVisible = ref(false)
const chunksLoading = ref(false)
const chunks = ref<ChunkVO[]>([])
const viewingDoc = ref<KnowledgeDocument | null>(null)
const expandedChunks = ref(new Set<number>())

// 轮询
const pollingMap = ref(new Map<number, ReturnType<typeof setInterval>>())

onMounted(async () => {
  try {
    const kb = await getKb(kbId)
    kbName.value = kb.name
  } catch { /* ignore */ }
  await loadDocs()
})

onUnmounted(() => {
  pollingMap.value.forEach(t => clearInterval(t))
})

async function loadDocs() {
  try {
    const res = await listDocuments(kbId, { pageSize: 100 })
    docList.value = res.list
    // 对 PENDING/PROCESSING 状态的文档开启轮询
    for (const doc of res.list) {
      if (doc.status === 'PENDING' || doc.status === 'PROCESSING') {
        startPolling(doc.id)
      }
    }
  } catch { /* ignore */ }
}

function startPolling(docId: number) {
  if (pollingMap.value.has(docId)) return
  const timer = setInterval(async () => {
    try {
      const updated = await getDocument(docId)
      const idx = docList.value.findIndex(d => d.id === docId)
      if (idx >= 0) docList.value[idx] = updated
      if (updated.status === 'DONE' || updated.status === 'FAILED') {
        clearInterval(pollingMap.value.get(docId))
        pollingMap.value.delete(docId)
      }
    } catch { /* ignore */ }
  }, 3000)
  pollingMap.value.set(docId, timer)
}

// 上传
function beforeUpload(file: UploadRawFile) {
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!['txt', 'md', 'pdf'].includes(ext ?? '')) {
    ElMessage.error('只支持 TXT / MD / PDF 格式')
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  return true
}

function onFileChange(_file: UploadFile, list: UploadFile[]) {
  fileList.value = list
}

async function doUpload() {
  if (fileList.value.length === 0) return
  uploading.value = true
  try {
    for (const f of fileList.value) {
      if (!f.raw) continue
      const doc = await uploadDocument(kbId, f.raw)
      docList.value.unshift(doc)
      startPolling(doc.id)
    }
    fileList.value = []
    uploadVisible.value = false
    ElMessage.success('上传成功，正在后台处理中…')
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '上传失败')
  } finally {
    uploading.value = false
  }
}

// 分块预览
async function viewChunks(doc: KnowledgeDocument) {
  viewingDoc.value = doc
  chunksVisible.value = true
  chunksLoading.value = true
  expandedChunks.value = new Set()
  try {
    chunks.value = await getChunks(doc.id)
  } finally {
    chunksLoading.value = false
  }
}

function toggleChunk(id: number) {
  if (expandedChunks.value.has(id)) expandedChunks.value.delete(id)
  else expandedChunks.value.add(id)
}

// 删除
async function onDelete(doc: KnowledgeDocument) {
  try {
    await ElMessageBox.confirm(`确定删除文档「${doc.name}」？`, '提示', { type: 'warning' })
    await deleteDocument(doc.id)
    docList.value = docList.value.filter(d => d.id !== doc.id)
    clearInterval(pollingMap.value.get(doc.id))
    pollingMap.value.delete(doc.id)
    ElMessage.success('删除成功')
  } catch { /* cancel */ }
}

// 工具函数
const STATUS_MAP = {
  PENDING:    { label: '待处理', type: 'info' },
  PROCESSING: { label: '处理中', type: 'primary' },
  DONE:       { label: '已完成', type: 'success' },
  FAILED:     { label: '失败',   type: 'danger' },
} as const

function statusLabel(s: string) { return STATUS_MAP[s as keyof typeof STATUS_MAP]?.label ?? s }
function statusType(s: string) { return STATUS_MAP[s as keyof typeof STATUS_MAP]?.type ?? 'info' }

function formatSize(bytes: number) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function formatTime(iso: string) {
  const d = new Date(iso)
  return `${d.getMonth()+1}/${d.getDate()} ${d.toTimeString().slice(0,5)}`
}
</script>

<style scoped>
.page-header { margin-bottom: 16px; }

.hify-card {
  background: var(--color-bg-card, #fff);
  border: 1px solid var(--color-border-default, #e3e6ef);
  border-radius: 8px;
  overflow: hidden;
}

.doc-name {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 13.5px;
  font-weight: 500;
}
.doc-icon { color: var(--color-primary-400, #818cf8); flex-shrink: 0; }

.status-cell { display: flex; align-items: center; gap: 6px; }
.error-icon { color: var(--color-danger-500, #ef4444); cursor: pointer; }

.chunk-count { font-weight: 600; color: var(--color-primary-500, #6366f1); }
.text-muted { color: var(--color-text-tertiary, #8b92a8); }

.table-empty { padding: 40px 0; }

.upload-tip {
  font-size: 12px;
  color: var(--color-text-tertiary, #8b92a8);
  margin-top: 6px;
  text-align: center;
}

/* 分块列表 */
.chunks-list {
  max-height: 60vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.chunk-item {
  border: 1px solid var(--color-border-default, #e3e6ef);
  border-radius: 8px;
  padding: 12px 14px;
}
.chunk-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.chunk-index {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-primary-500, #6366f1);
  background: var(--color-primary-50, #eef2ff);
  padding: 2px 8px;
  border-radius: 4px;
}
.chunk-tokens {
  font-size: 11px;
  color: var(--color-text-tertiary, #8b92a8);
}
.chunk-content {
  font-size: 13px;
  line-height: 1.65;
  color: var(--color-text-primary, #0f1117);
  max-height: 60px;
  overflow: hidden;
  transition: max-height 0.3s;
}
.chunk-content.expanded { max-height: none; }

/* 旋转动画 */
.rotating {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
</style>
