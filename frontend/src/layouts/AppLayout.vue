<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ChatDotRound, Collection, DataAnalysis, Document, House, Setting } from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => `/${route.path.split('/')[1] ?? ''}`)

const navItems = [
  { path: '/home', title: '首页', icon: House },
  { path: '/archives', title: '资料库', icon: Collection },
  { path: '/questions', title: '错题', icon: Document },
]

// Visible but disabled in the MVP (AGENTS.md §16).
const plannedItems = [
  { key: 'assistant', title: 'AI 助手', icon: ChatDotRound },
  { key: 'profile', title: '学习画像', icon: DataAnalysis },
]
</script>

<template>
  <el-container class="layout">
    <el-header class="layout__header">
      <div class="layout__brand">AI Study</div>
      <el-tooltip content="学习空间切换将在 M1 实现" placement="bottom">
        <el-select class="layout__space" model-value="" placeholder="默认学习空间" disabled />
      </el-tooltip>
      <div class="layout__spacer" />
      <RouterLink to="/settings" class="layout__settings" title="设置">
        <el-icon :size="18"><Setting /></el-icon>
      </RouterLink>
    </el-header>

    <el-container>
      <el-aside width="200px" class="layout__aside">
        <el-menu :default-active="activeMenu" router class="layout__menu">
          <el-menu-item v-for="item in navItems" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
          <el-menu-item v-for="item in plannedItems" :key="item.key" :index="item.key" disabled>
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
            <el-tag size="small" type="info" class="layout__planned">规划中</el-tag>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="layout__main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

.layout__header {
  display: flex;
  align-items: center;
  gap: 16px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}

.layout__brand {
  font-size: 18px;
  font-weight: 600;
}

.layout__space {
  width: 200px;
}

.layout__spacer {
  flex: 1;
}

.layout__settings {
  display: flex;
  color: var(--el-text-color-regular);
}

.layout__aside {
  border-right: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}

.layout__menu {
  border-right: none;
}

.layout__planned {
  margin-left: 8px;
}

.layout__main {
  padding: 24px;
}
</style>
