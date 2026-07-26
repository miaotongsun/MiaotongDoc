<template>
  <div class="login-page">
    <div class="bg-decoration">
      <div class="bg-grain"></div>
    </div>

    <!-- Brand showcase -->
    <div class="brand-section">
      <div class="brand-inner">
        <div class="brand-seal">
          <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
            <!-- Outer double ring (seal-like) -->
            <circle cx="32" cy="32" r="28" stroke="currentColor" stroke-width="1.4" fill="none" />
            <circle cx="32" cy="32" r="24" stroke="currentColor" stroke-width="0.8" fill="none" opacity="0.4" />
            <!-- Central 同 (tong) character made of strokes -->
            <rect x="14" y="20" width="36" height="3" rx="1.5" fill="currentColor" />
            <rect x="14" y="41" width="36" height="3" rx="1.5" fill="currentColor" />
            <rect x="30.5" y="20" width="3" height="24" fill="currentColor" />
            <!-- Four corner seal dots -->
            <circle cx="14" cy="20" r="1.6" fill="currentColor" />
            <circle cx="50" cy="20" r="1.6" fill="currentColor" />
            <circle cx="14" cy="43" r="1.6" fill="currentColor" />
            <circle cx="50" cy="43" r="1.6" fill="currentColor" />
          </svg>
        </div>

        <div class="brand-title-block">
          <span class="brand-cn">妙同</span>
          <span class="brand-cn-sub">文档</span>
        </div>

        <div class="brand-en">MiaotongDoc</div>

        <div class="brand-spacer-1"></div>

        <p class="brand-tagline">妙思互通 · 同心同步</p>

        <div class="brand-spacer-2"></div>

        <div class="brand-divider"></div>

        <div class="brand-spacer-3"></div>

        <p class="brand-desc">执笔如水，流转如风<br />一纸落墨，众人同舟</p>

        <div class="brand-points">
          <div class="point">
            <span class="point-num">壹</span>
            <span class="point-text">同舟共济 · 一人起草众人共修，千端并进</span>
          </div>
          <div class="point">
            <span class="point-num">贰</span>
            <span class="point-text">同心同德 · 版本有迹可循，权限有托可依</span>
          </div>
          <div class="point">
            <span class="point-num">叁</span>
            <span class="point-text">同行同契 · 落字即成契约，千里亦可同守</span>
          </div>
          <div class="point">
            <span class="point-num">肆</span>
            <span class="point-text">同智同进 · AI 常伴左右，繁务不劳心神</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Login panel -->
    <div class="login-section">
      <div class="login-card">
        <div class="card-header">
          <h2 class="card-title">登 录</h2>
          <p class="card-subtitle">欢迎回到 MiaotongDoc</p>
        </div>

        <el-form :model="form" @submit.prevent="handleLogin" class="login-form">
          <el-form-item>
            <el-input
              v-model="form.username"
              placeholder="工号"
              :prefix-icon="User"
              size="large"
              class="custom-input"
            />
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              :prefix-icon="Lock"
              show-password
              size="large"
              class="custom-input"
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-button
            :loading="loading"
            @click="handleLogin"
            class="login-btn"
            size="large"
          >
            <span class="login-btn-text">{{ loading ? '登录中' : '登 录' }}</span>
            <svg class="login-btn-arrow" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M4 10h11M11 5l5 5-5 5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </el-button>
        </el-form>

        <template v-if="ssoEnabled">
          <div class="divider">
            <span>其他登录方式</span>
          </div>
          <el-button class="sso-btn" @click="ssoLogin" :loading="ssoLoading" size="large">
            {{ ssoProviderName }}
          </el-button>
        </template>

        <p class="copyright">© {{ new Date().getFullYear() }} MiaotongDoc</p>
      </div>
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

  if (route.query.error === 'sso_failed') {
    ElMessage.error('企业账号登录失败，请重试或使用本地账号')
  }

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
    const status = error.response?.status
    const message = error.response?.data?.message

    if (status === 400) {
      ElMessage.error(message || '用户名或密码错误')
    } else if (status === 401) {
      ElMessage.error('用户名或密码错误')
    } else if (status === 403) {
      ElMessage.error('账号已被禁用，请联系管理员')
    } else if (status === 500) {
      ElMessage.error('服务器错误，请稍后重试')
    } else {
      ElMessage.error(message || '登录失败，请检查网络连接')
    }
  } finally {
    loading.value = false
  }
}

function ssoLogin() {
  ssoLoading.value = true
  window.location.href = '/api/sso/login'
}
</script>

<style>
@import url('https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@200;300;400;500;600;700&family=Inter:wght@300;400;500;600&family=Cormorant+Garamond:wght@300;400;500&display=swap');
</style>

<style scoped>
.login-page {
  position: relative;
  width: 100%;
  min-height: 100vh;
  display: flex;
  overflow: hidden;
  background: #f5f1ea;
  font-family: 'Inter', 'Noto Serif SC', -apple-system, BlinkMacSystemFont, sans-serif;
}

/* ===== Background ===== */
.bg-decoration {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  color: #8a8a8a;
}

.bg-grain {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 25% 35%, rgba(201, 169, 97, 0.07) 0%, transparent 45%),
    radial-gradient(circle at 75% 65%, rgba(58, 80, 107, 0.035) 0%, transparent 45%),
    linear-gradient(135deg, #f7f3ed 0%, #ede7dc 50%, #e8e0d3 100%);
}

.bg-ink {
  display: none;
}

.bg-ripple {
  display: none;
}

/* ===== Brand Section (Left) ===== */
.brand-section {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 80px 60px 60px;
}

.brand-inner {
  max-width: 420px;
  width: 100%;
  text-align: center;
}

.brand-seal {
  width: 52px;
  height: 52px;
  color: #1a2332;
  margin: 0 auto 44px;
  opacity: 0.85;
}

.brand-title-block {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 12px;
  margin-bottom: 20px;
}

.brand-cn {
  font-family: 'Noto Serif SC', serif;
  font-size: 72px;
  font-weight: 500;
  color: #1a2332;
  letter-spacing: 14px;
  line-height: 1;
  text-indent: 14px;
}

.brand-cn-sub {
  font-family: 'Noto Serif SC', serif;
  font-size: 22px;
  font-weight: 300;
  color: #8a8a8a;
  letter-spacing: 10px;
  text-indent: 10px;
}

.brand-en {
  display: block;
  font-family: 'Cormorant Garamond', serif;
  font-size: 18px;
  font-weight: 400;
  color: #8a8a8a;
  letter-spacing: 8px;
  text-transform: uppercase;
  margin: 0 auto;
  text-indent: 8px;
}

.brand-spacer-1 {
  height: 64px;
}

.brand-tagline {
  font-family: 'Noto Serif SC', serif;
  font-size: 24px;
  font-weight: 400;
  color: #1a2332;
  margin: 0 0 0;
  letter-spacing: 10px;
}

.brand-spacer-2 {
  height: 48px;
}

.brand-divider {
  width: 48px;
  height: 1px;
  background: #c9a961;
  margin: 0 auto;
}

.brand-spacer-3 {
  height: 48px;
}

.brand-desc {
  font-family: 'Noto Serif SC', serif;
  font-size: 16px;
  font-weight: 300;
  color: #5a6478;
  margin: 0 0 48px;
  line-height: 2.4;
  letter-spacing: 4px;
}

.brand-points {
  display: flex;
  flex-direction: column;
  gap: 18px;
  text-align: left;
  max-width: 340px;
  margin: 0 auto;
}

.point {
  display: flex;
  align-items: baseline;
  gap: 18px;
  padding-bottom: 14px;
  border-bottom: 1px dashed rgba(201, 169, 97, 0.25);
}

.point:last-child {
  border-bottom: none;
}

.point-num {
  font-family: 'Noto Serif SC', serif;
  font-size: 14px;
  font-weight: 500;
  color: #c9a961;
  letter-spacing: 1px;
  min-width: 24px;
}

.point-text {
  font-family: 'Noto Serif SC', serif;
  font-size: 14px;
  font-weight: 400;
  color: #3a4555;
  letter-spacing: 1.5px;
}

/* ===== Login Section (Right) ===== */
.login-section {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 60px 60px 80px;
}

.login-card {
  width: 100%;
  max-width: 380px;
}

.card-header {
  margin-bottom: 40px;
}

.card-title {
  font-family: 'Noto Serif SC', serif;
  font-size: 32px;
  font-weight: 500;
  color: #1a2332;
  margin: 0 0 10px;
  letter-spacing: 10px;
}

.card-subtitle {
  font-size: 13px;
  color: #8a8a8a;
  margin: 0;
  letter-spacing: 1px;
}

/* Form */
.login-form {
  margin-bottom: 4px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 28px;
}

/* Override Element Plus default styles completely */
.login-form :deep(.el-form-item__content),
.login-form :deep(.el-input),
.login-form :deep(.el-input__wrapper),
.login-form :deep(.el-input__wrapper.is-focus),
.login-form :deep(.el-input__wrapper:hover) {
  background: transparent !important;
  background-color: transparent !important;
}

.custom-input :deep(.el-input) {
  background: transparent !important;
}

.custom-input :deep(.el-input__wrapper) {
  background: transparent !important;
  background-color: transparent !important;
  border: none !important;
  box-shadow: none !important;
  border-radius: 0 !important;
  padding: 4px 0 !important;
  border-bottom: 1px solid #d4ccc0 !important;
  transition: all 0.3s ease !important;
}

.custom-input :deep(.el-input__wrapper:hover) {
  border-bottom-color: #a89880 !important;
}

.custom-input :deep(.el-input__wrapper.is-focus) {
  border-bottom-color: #1a2332 !important;
  box-shadow: 0 1px 0 0 #1a2332 !important;
}

.custom-input :deep(.el-input__inner) {
  background: transparent !important;
  color: #1a2332 !important;
  font-size: 15px;
  height: 44px;
  font-family: 'Inter', sans-serif;
  box-shadow: none !important;
}

.custom-input :deep(.el-input__inner::placeholder) {
  color: #b8b0a4;
  font-size: 14px;
  font-family: 'Noto Serif SC', serif;
  letter-spacing: 1.5px;
}

.custom-input :deep(.el-input__prefix-inner) {
  color: #a89880;
  font-size: 16px;
}

.custom-input :deep(.el-input__wrapper.is-focus .el-input__prefix-inner) {
  color: #1a2332;
}

.custom-input :deep(.el-input__suffix-inner) {
  color: #a89880;
}

/* Login button - subtle, refined */
.login-btn {
  width: 100%;
  height: 52px;
  font-size: 14px;
  font-weight: 400;
  letter-spacing: 6px;
  border-radius: 0;
  background: #1a2332;
  border: none;
  font-family: 'Noto Serif SC', serif;
  transition: all 0.35s ease;
  margin-top: 16px;
  text-indent: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #f5f1ea;
  position: relative;
  overflow: hidden;
}

.login-btn::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent 0%, rgba(201, 169, 97, 0.15) 50%, transparent 100%);
  transform: translateX(-100%);
  transition: transform 0.6s ease;
}

.login-btn:hover::before {
  transform: translateX(100%);
}

.login-btn:hover {
  background: #2c3e50;
  letter-spacing: 8px;
  box-shadow: 0 8px 28px rgba(26, 35, 50, 0.22);
}

.login-btn:active {
  transform: translateY(0);
}

.login-btn-text {
  position: relative;
  z-index: 1;
}

.login-btn-arrow {
  position: relative;
  z-index: 1;
  width: 18px;
  height: 18px;
  transition: transform 0.35s ease;
}

.login-btn:hover .login-btn-arrow {
  transform: translateX(4px);
}

/* Divider */
.divider {
  display: flex;
  align-items: center;
  margin: 28px 0;
  gap: 16px;
  color: #a89880;
  font-size: 12px;
  font-family: 'Noto Serif SC', serif;
  letter-spacing: 2px;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #d4ccc0;
}

/* SSO button */
.sso-btn {
  width: 100%;
  height: 48px;
  font-size: 13px;
  font-weight: 400;
  letter-spacing: 1.5px;
  border-radius: 0;
  border: 1.5px solid #d4ccc0;
  background: transparent;
  color: #3a4555;
  font-family: 'Noto Serif SC', serif;
  transition: all 0.25s ease;
}

.sso-btn:hover {
  border-color: #1a2332;
  color: #1a2332;
  background: rgba(26, 35, 50, 0.02);
}

.copyright {
  margin: 36px 0 0;
  font-size: 11px;
  color: #b8b0a4;
  text-align: center;
  font-family: 'Inter', sans-serif;
  letter-spacing: 1.5px;
}

/* ===== Responsive ===== */
@media (max-width: 960px) {
  .login-page {
    flex-direction: column;
  }

  .brand-section {
    flex: none;
    padding: 48px 32px 24px;
  }

  .brand-inner {
    text-align: left;
  }

  .brand-points {
    display: none;
  }

  .brand-cn {
    font-size: 56px;
  }

  .brand-seal {
    width: 44px;
    height: 44px;
    margin-bottom: 24px;
  }

  .login-section {
    flex: none;
    padding: 24px 32px 48px;
  }
}

@media (max-width: 480px) {
  .brand-section {
    padding: 36px 24px 20px;
  }

  .brand-title-block {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .brand-cn {
    font-size: 44px;
    letter-spacing: 10px;
    text-indent: 10px;
  }

  .brand-en {
    padding-left: 0;
    border-left: none;
    font-size: 16px;
  }

  .login-section {
    padding: 20px 24px 36px;
  }

  .card-title {
    font-size: 26px;
    letter-spacing: 8px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-btn,
  .login-btn-arrow,
  .login-btn::before {
    transition: none !important;
  }

  .login-btn:hover {
    transform: none;
  }

  .login-btn:hover .login-btn-arrow {
    transform: none;
  }
}
</style>