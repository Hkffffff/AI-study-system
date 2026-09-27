<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchHealth, type HealthStatus } from '@/api/health'
import type { ApiError } from '@/api/errors'

const loading = ref(false)
const health = ref<HealthStatus | null>(null)
const error = ref<ApiError | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    health.value = await fetchHealth()
  } catch (e) {
    health.value = null
    error.value = e as ApiError
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="home__header">
        <span>系统状态</span>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </template>

    <el-alert v-if="error" type="error" :title="error.message" :closable="false" show-icon />

    <el-descriptions v-else-if="health" :column="1" border>
      <el-descriptions-item label="后端">
        <el-tag :type="health.status === 'UP' ? 'success' : 'warning'">{{ health.status }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="数据库">
        <el-tag :type="health.database === 'UP' ? 'success' : 'danger'">{{ health.database }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="检查时间">{{ new Date(health.time).toLocaleString() }}</el-descriptions-item>
    </el-descriptions>

    <el-skeleton v-else :rows="3" animated />
  </el-card>
</template>

<style scoped>
.home__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
