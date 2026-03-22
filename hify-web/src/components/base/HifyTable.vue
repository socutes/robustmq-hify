<template>
  <div class="hify-table-wrap">
    <el-table
      v-loading="loading"
      :data="rows"
      :row-style="rowStyle"
      stripe
      style="width: 100%"
    >
      <template v-for="col in visibleColumns">
        <el-table-column
          v-if="col.slot"
          :key="`slot-${col.slot}`"
          :label="col.label"
          :width="col.width"
          :min-width="col.minWidth"
        >
          <template #default="scope">
            <slot :name="col.slot" v-bind="scope" />
          </template>
        </el-table-column>
        <el-table-column
          v-else
          :key="`prop-${col.prop}`"
          :prop="col.prop"
          :label="col.label"
          :width="col.width"
          :min-width="col.minWidth"
          :show-overflow-tooltip="col.ellipsis ?? true"
        />
      </template>
    </el-table>

    <el-empty v-if="!loading && rows.length === 0" description="暂无数据" :image-size="80" />

    <div v-if="showPagination && total > 0" class="hify-table-pagination">
      <el-pagination
        :current-page="page"
        :page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'

export interface TableColumn {
  label: string
  prop?: string
  slot?: string
  width?: number | string
  minWidth?: number | string
  ellipsis?: boolean
  hideOnNarrow?: boolean
}

interface PageResult {
  list: Record<string, unknown>[]
  total: number
}

interface Props {
  columns: TableColumn[]
  api: (params: { page: number; pageSize: number }) => Promise<PageResult>
  showPagination?: boolean
  rowStyle?: Record<string, string>
}

const props = withDefaults(defineProps<Props>(), {
  showPagination: true,
  rowStyle: () => ({}),
})

const rows = ref<Record<string, unknown>[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const isNarrow = ref(window.innerWidth < 1200)

const onResize = () => { isNarrow.value = window.innerWidth < 1200 }
onMounted(() => window.addEventListener('resize', onResize))
onUnmounted(() => window.removeEventListener('resize', onResize))

const visibleColumns = computed(() =>
  props.columns.filter(col => !(isNarrow.value && col.hideOnNarrow))
)

const load = async () => {
  loading.value = true
  try {
    const res = await props.api({ page: page.value, pageSize: pageSize.value })
    rows.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const onPageChange = (v: number) => { page.value = v; load() }
const onSizeChange = (v: number) => { pageSize.value = v; page.value = 1; load() }

const refresh = () => { page.value = 1; load() }

onMounted(load)
defineExpose({ refresh })
</script>

<style scoped>
.hify-table-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.hify-table-pagination {
  display: flex;
  justify-content: flex-end;
}
</style>
