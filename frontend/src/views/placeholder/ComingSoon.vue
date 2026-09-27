<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <el-empty :description="`${route.meta.title ?? '该页面'}将在 ${route.meta.milestone ?? '后续里程碑'} 实现`" />
</template>
