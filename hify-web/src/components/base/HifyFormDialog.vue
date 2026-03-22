<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="width"
    :close-on-click-modal="false"
    destroy-on-close
    @closed="onClosed"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-width="labelWidth"
      label-position="right"
      @submit.prevent
    >
      <slot :form="formData" :mode="mode" />
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">
        {{ mode === 'edit' ? '保存' : '确认' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

interface Props {
  title: string
  width?: string
  labelWidth?: string
  rules?: FormRules
}

withDefaults(defineProps<Props>(), {
  width: '520px',
  labelWidth: '90px',
  rules: () => ({}),
})

const emit = defineEmits<{
  submit: [data: Record<string, unknown>, mode: 'add' | 'edit']
}>()

const visible = ref(false)
const submitting = ref(false)
const mode = ref<'add' | 'edit'>('add')
const formData = ref<Record<string, unknown>>({})
const formRef = ref<FormInstance>()

const open = (data?: Record<string, unknown>) => {
  mode.value = data ? 'edit' : 'add'
  formData.value = data ? { ...data } : {}
  visible.value = true
}

const onSubmit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    emit('submit', { ...formData.value }, mode.value)
  } finally {
    submitting.value = false
  }
}

const onClosed = () => {
  formRef.value?.resetFields()
  formData.value = {}
}

const close = () => { visible.value = false }

defineExpose({ open, close, submitting })
</script>
