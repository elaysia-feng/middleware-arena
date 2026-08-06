<template>
  <el-container class="main-container">
    <el-aside width="220px" class="main-aside">
      <el-menu
        :default-active="activeMenu"
        router
        class="main-menu"
      >
        <!-- TODO: 菜单项路由 + icon 完善 -->
        <el-menu-item index="/">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/experiment">
          <el-icon><Opportunity /></el-icon>
          <span>实验</span>
        </el-menu-item>
        <el-menu-item index="/community">
          <el-icon><User /></el-icon>
          <span>社区</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="main-header">
        <div class="header-left">
          <h2>Middleware Arena</h2>
        </div>
        <div class="header-right">
          <!-- TODO: 展示当前登录用户名，未登录显示登录按钮 -->
          <span class="username-placeholder">未登录</span>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { HomeFilled, Opportunity, User } from '@element-plus/icons-vue'

const route = useRoute()

const activeMenu = computed(() => {
  const path = route.path
  if (path === '/') return '/'
  return path
})
</script>

<style scoped>
.main-container {
  height: 100vh;
}

.main-aside {
  background-color: #fafafa;
  border-right: 1px solid #e4e7ed;
}

.main-menu {
  border-right: none;
  height: 100%;
}

.main-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
  padding: 0 20px;
}

.main-header h2 {
  margin: 0;
  font-size: 18px;
}

.username-placeholder {
  color: #909399;
  font-size: 14px;
}

.main-content {
  background: #f5f7fa;
  padding: 20px;
}
</style>
