<template>
  <div class="error-wrapper">
    <el-result icon="warning" title="404" sub-title="你访问的页面不存在">
      <template #extra>
        <el-button type="primary" @click="$router.push('/')">回到首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
// 404 兜底页面
</script>

<style scoped>
.error-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
}
</style>
