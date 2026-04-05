<template>
  <div class="page-container">
    <div class="page-header">
      <div class="page-header-left">
        <div class="page-title">知识库管理</div>
        <div class="page-desc">管理 RAG 知识库，上传文档自动分块向量化</div>
      </div>
      <div class="page-header-actions">
        <el-input
          v-model="searchName"
          placeholder="搜索知识库名称"
          clearable
          style="width: 200px; margin-right: 10px"
          @input="onSearch"
        />
        <button class="btn-primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          新建知识库
        </button>
      </div>
    </div>

    <div class="kb-grid">
      <div
        v-for="kb in kbList"
        :key="kb.id"
        class="kb-card"
        @click="goDocuments(kb.id)"
      >
        <div class="kb-card-header">
          <div class="kb-icon">
            <el-icon :size="22"><Folder /></el-icon>
          </div>
          <div class="kb-actions" @click.stop>
            <el-tag :type="kb.enabled ? 'success' : 'info'" size="small" style="margin-right:6px">
              {{ kb.enabled ? '启用' : '禁用' }}
            </el-tag>
            <el-button type="primary" link size="small" @click="openEdit(kb)">编辑</el-button>
            <el-button type="danger" link size="small" @click="onDelete(kb)">删除</el-button>
          </div>
        </div>
        <div class="kb-name">{{ kb.name }}</div>
        <div class="kb-desc">{{ kb.description || '暂无描述' }}</div>
        <div class="kb-footer">
          <span class="kb-time">{{ formatDate(kb.createdAt) }}</span>
          <span class="kb-link">查看文档 →</span>
        </div>
      </div>

      <div v-if="kbList.length === 0 && !loading" class="kb-empty">
        <el-empty description="还没有知识库，点击「新建知识库」开始" />
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="total > pageSize" class="page-pagination">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadList"
      />
    </div>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingKb ? '编辑知识库' : '新建知识库'"
      width="440px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
        <el-form-item v-if="editingKb" label="状态">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Folder } from '@element-plus/icons-vue'
import { listKb, createKb, updateKb, deleteKb } from '@/api/knowledge'
import type { KnowledgeBase } from '@/api/knowledge'

const router = useRouter()

const kbList = ref<KnowledgeBase[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = 12
const searchName = ref('')

const dialogVisible = ref(false)
const saving = ref(false)
const editingKb = ref<KnowledgeBase | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ name: '', description: '', enabled: 1 as number })

const rules: FormRules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
}

onMounted(loadList)

async function loadList() {
  loading.value = true
  try {
    const res = await listKb({ page: page.value, pageSize, name: searchName.value || undefined })
    kbList.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

let searchTimer: ReturnType<typeof setTimeout>
function onSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 1; loadList() }, 300)
}

function openCreate() {
  editingKb.value = null
  form.name = ''
  form.description = ''
  form.enabled = 1
  dialogVisible.value = true
}

function openEdit(kb: KnowledgeBase) {
  editingKb.value = kb
  form.name = kb.name
  form.description = kb.description
  form.enabled = kb.enabled
  dialogVisible.value = true
}

async function onSubmit() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editingKb.value) {
      await updateKb(editingKb.value.id, { name: form.name, description: form.description, enabled: form.enabled })
      ElMessage.success('保存成功')
    } else {
      await createKb({ name: form.name, description: form.description })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function onDelete(kb: KnowledgeBase) {
  try {
    await ElMessageBox.confirm(`确定删除知识库「${kb.name}」及其所有文档？`, '提示', { type: 'warning' })
    await deleteKb(kb.id)
    ElMessage.success('删除成功')
    loadList()
  } catch { /* cancel */ }
}

function goDocuments(kbId: number) {
  router.push(`/knowledge/${kbId}/documents`)
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.page-header { margin-bottom: 20px; }

.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.kb-card {
  background: var(--color-bg-card, #fff);
  border: 1px solid var(--color-border-default, #e3e6ef);
  border-radius: 10px;
  padding: 20px;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s, transform 0.15s;
}
.kb-card:hover {
  box-shadow: 0 4px 16px rgba(99,102,241,0.12);
  border-color: var(--color-primary-300, #a5b4fc);
  transform: translateY(-2px);
}

.kb-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}
.kb-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: linear-gradient(135deg, #eef2ff, #e0e7ff);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary-500, #6366f1);
}
.kb-actions { display: flex; align-items: center; gap: 2px; }

.kb-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary, #0f1117);
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.kb-desc {
  font-size: 13px;
  color: var(--color-text-secondary, #4b5268);
  line-height: 1.5;
  height: 40px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.kb-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-default, #e3e6ef);
}
.kb-time { font-size: 12px; color: var(--color-text-tertiary, #8b92a8); }
.kb-link { font-size: 12px; color: var(--color-primary-500, #6366f1); }

.kb-empty { grid-column: 1 / -1; padding: 60px 0; }

.page-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
