<template>
  <div class="hify-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ collapsed }">

      <!-- Logo -->
      <div class="sidebar-logo">
        <div class="logo-icon">H</div>
        <transition name="fade">
          <div v-if="!collapsed" class="logo-text">
            <span class="logo-brand">Hify</span>
            <span class="logo-sub">AI Agent Platform</span>
          </div>
        </transition>
      </div>

      <!-- 导航 -->
      <nav class="sidebar-nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: route.path === item.path }"
        >
          <el-icon :size="17"><component :is="item.icon" /></el-icon>
          <transition name="fade">
            <span v-if="!collapsed" class="nav-label">{{ item.label }}</span>
          </transition>
          <el-tooltip v-if="collapsed" :content="item.label" placement="right">
            <span class="tooltip-anchor" />
          </el-tooltip>
        </router-link>
      </nav>

      <!-- 底部 -->
      <div class="sidebar-footer">
        <transition name="fade">
          <span v-if="!collapsed" class="version">v0.0.1</span>
        </transition>
        <button class="collapse-btn" @click="collapsed = !collapsed">
          <el-icon :size="15">
            <component :is="collapsed ? ArrowRight : ArrowLeft" />
          </el-icon>
        </button>
      </div>

    </aside>

    <!-- 主内容区 -->
    <main class="hify-main">
      <!-- 顶栏 -->
      <div class="hify-topbar">
        <div class="topbar-breadcrumb">
          <span>首页</span>
          <span>/</span>
          <span class="current">{{ currentLabel }}</span>
        </div>
        <div class="topbar-user">
          <el-avatar :size="32" :style="{ background: 'var(--color-primary-500)' }">A</el-avatar>
          <span class="topbar-username">Admin</span>
        </div>
      </div>

      <div class="hify-content">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { Setting, User, ChatDotRound, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'

const route = useRoute()
const collapsed = ref(window.innerWidth < 1200)

const onResize = () => {
  if (window.innerWidth < 1200) collapsed.value = true
}
onMounted(() => window.addEventListener('resize', onResize))
onUnmounted(() => window.removeEventListener('resize', onResize))

const navItems = [
  { path: '/provider', label: '模型管理',   icon: Setting },
  { path: '/agent',    label: 'Agent 管理', icon: User },
  { path: '/chat',     label: '对话',       icon: ChatDotRound },
]

const currentLabel = computed(() => {
  const match = navItems.find(item => item.path === route.path)
  return match ? match.label : ''
})
</script>

<style scoped>
/* ── 侧边栏容器 ──────────────────────────────────────────── */
.sidebar {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background-color: #0d0f16;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  transition: width 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}
.sidebar.collapsed { width: 56px; }

/* ── Logo ────────────────────────────────────────────────── */
.sidebar-logo {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.logo-icon {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border-radius: 7px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  box-shadow: 0 0 12px rgba(99, 102, 241, 0.5);
}
.logo-text {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  white-space: nowrap;
}
.logo-brand {
  font-size: 15px;
  font-weight: 700;
  background: linear-gradient(90deg, #818cf8, #a78bfa);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  line-height: 1.3;
}
.logo-sub {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.3);
  letter-spacing: 0.04em;
  line-height: 1.4;
}

/* ── 导航 ────────────────────────────────────────────────── */
.sidebar-nav {
  flex: 1;
  padding: 8px 0;
  overflow-y: auto;
  overflow-x: hidden;
}
.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 40px;
  padding: 0 16px;
  margin: 1px 6px;
  border-radius: 6px;
  color: rgba(255, 255, 255, 0.55);
  font-size: 13.5px;
  font-weight: 500;
  text-decoration: none;
  white-space: nowrap;
  transition: background-color 0.15s, color 0.15s;
}
.nav-item:hover {
  background-color: rgba(255, 255, 255, 0.07);
  color: rgba(255, 255, 255, 0.9);
}
.nav-item.active {
  background-color: rgba(99, 102, 241, 0.15);
  color: #fff;
}
/* 选中态左侧竖线 */
.nav-item.active::before {
  content: '';
  position: absolute;
  left: -6px;
  top: 25%;
  height: 50%;
  width: 3px;
  background: linear-gradient(180deg, #6366f1, #8b5cf6);
  border-radius: 0 2px 2px 0;
}
/* collapsed 时图标居中 */
.sidebar.collapsed .nav-item {
  padding: 0;
  justify-content: center;
  margin: 1px 8px;
}
.tooltip-anchor {
  position: absolute;
  inset: 0;
}
.nav-label { flex: 1; }

/* ── 底部 ────────────────────────────────────────────────── */
.sidebar-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}
.version {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.2);
  white-space: nowrap;
}
.collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  border-radius: 5px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: transparent;
  color: rgba(255, 255, 255, 0.4);
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
  margin-left: auto;
}
.collapse-btn:hover {
  background-color: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.8);
}

/* ── 折叠过渡 ─────────────────────────────────────────────── */
.fade-enter-active { transition: opacity 0.15s 0.1s; }
.fade-leave-active { transition: opacity 0.08s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
