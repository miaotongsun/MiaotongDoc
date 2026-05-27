<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-header">
        <h1>MiaotongDoc</h1>
        <p class="slogan">妙思互通，同心同步</p>
      </div>

      <el-form :model="form" @submit.prevent="handleLogin" class="login-form">
        <el-form-item label="工号">
          <el-input v-model="form.username" placeholder="请输入工号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock"
            show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="handleLogin" class="login-btn">
          登录
        </el-button>
      </el-form>

      <template v-if="ssoEnabled">
        <div class="divider">
          <span>或者</span>
        </div>
        <el-button class="sso-btn" @click="ssoLogin" :loading="ssoLoading">
          {{ ssoProviderName }}
        </el-button>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ssoApi } from '@/api/sso'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const form = ref({
  username: '',
  password: ''
})
const loading = ref(false)
const ssoEnabled = ref(false)
const ssoProviderName = ref('企业账号登录')
const ssoLoading = ref(false)

onMounted(async () => {
  // 检查 SSO 回调
  const hash = window.location.hash
  if (hash.includes('token=')) {
    const params = new URLSearchParams(hash.substring(1))
    const token = params.get('token')
    if (token) {
      sessionStorage.setItem('token', token)
      sessionStorage.setItem('userId', params.get('userId') || '')
      sessionStorage.setItem('name', params.get('name') || '')
      sessionStorage.setItem('employeeId', params.get('employeeId') || '')
      history.replaceState(null, '', window.location.pathname)
      router.push('/home')
      return
    }
  }

  // 检查 SSO 错误
  if (route.query.error === 'sso_failed') {
    ElMessage.error('企业账号登录失败，请重试或使用本地账号')
  }

  // 获取 SSO 提供商
  try {
    const providers = await ssoApi.getProviders()
    if (providers.length > 0) {
      ssoEnabled.value = true
      ssoProviderName.value = providers[0].name || '企业账号登录'
    }
  } catch {}
})

async function handleLogin() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入工号和密码')
    return
  }

  loading.value = true
  try {
    await userStore.login(form.value.username, form.value.password)
    router.push('/home')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '登录失败')
  } finally {
    loading.value = false
  }
}

function ssoLogin() {
  ssoLoading.value = true
  window.location.href = '/api/sso/login'
}
</script>

<style scoped>
.login-page {
  width: 100%;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-container {
  width: 400px;
  padding: 40px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.login-header h1 {
  font-size: 32px;
  color: #333;
  margin-bottom: 8px;
}

.slogan {
  color: #666;
  font-size: 14px;
}

.login-form {
  margin-bottom: 20px;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
}

.divider {
  display: flex;
  align-items: center;
  margin: 20px 0;
  color: #999;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #ddd;
}

.divider span {
  padding: 0 15px;
}

.sso-btn {
  width: 100%;
  height: 44px;
}
</style>
