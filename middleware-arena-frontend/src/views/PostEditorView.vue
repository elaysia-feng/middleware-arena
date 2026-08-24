<template>
  <div class="editor-wrapper" v-loading="loading">
    <el-card>
      <template #header>
        <h2>{{ isEdit ? '编辑帖子' : '发布新帖' }}</h2>
      </template>
      <el-form :model="form" :rules="rules" label-position="top">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="80" show-word-limit />
        </el-form-item>
        <el-form-item label="正文" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="12"
            maxlength="10000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="标签（逗号分隔；后端当前不持久化，预留字段）">
          <el-input
            v-model="form.tagsText"
            placeholder="如：redis, 压测, 中间件"
          />
        </el-form-item>
        <el-form-item>
          <el-space>
            <el-button type="primary" :loading="submitting" @click="handleSubmit">
              {{ isEdit ? '保存' : '发布' }}
            </el-button>
            <el-button @click="$router.back()">取消</el-button>
          </el-space>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormRules } from 'element-plus'
import { createPost, updatePost, getPost } from '@/api/community'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)

const postId = computed(() => {
  const id = route.params.postId
  return id ? Number(id) : undefined
})
const isEdit = computed(() => postId.value !== undefined)

// 后端 CreatePostRequest.tags 是单个 String（逗号分隔），不是 string[]
const form = reactive({
  title: '',
  content: '',
  tagsText: '',
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入正文', trigger: 'blur' }],
}

async function loadExisting() {
  if (!isEdit.value || postId.value === undefined) return
  loading.value = true
  try {
    const post = await getPost(postId.value)
    form.title = post.title
    form.content = post.content
    // 后端 PostResponse 没有 tags 字段，无法预填
    form.tagsText = ''
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function buildPayload() {
  const tags = form.tagsText.trim()
  return {
    title: form.title.trim(),
    content: form.content.trim(),
    tags: tags || undefined,
  }
}

async function handleSubmit() {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('标题和正文不能为空')
    return
  }
  submitting.value = true
  try {
    const payload = buildPayload()
    if (isEdit.value && postId.value !== undefined) {
      await updatePost(postId.value, payload)
      ElMessage.success('已保存')
      router.replace(`/community/post/${postId.value}`)
    } else {
      const created = await createPost(payload)
      ElMessage.success('发布成功')
      router.replace(`/community/post/${created.id}`)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    submitting.value = false
  }
}

onMounted(loadExisting)
</script>

<style scoped>
.editor-wrapper {
  max-width: 900px;
  margin: 0 auto;
}
</style>
