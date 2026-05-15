<template>
  <div class="login-page">
    <div class="orb orb-a"></div>
    <div class="orb orb-b"></div>

    <section class="login-intro">
      <div class="brand-mark">
        <svg viewBox="0 0 24 24" fill="none">
          <path d="M12 3L3 7.5L12 12L21 7.5L12 3Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
          <path d="M3 12L12 16.5L21 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          <path d="M3 16.5L12 21L21 16.5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>
      <span class="eyebrow">WUJO RAG</span>
      <h1>让企业资料变成可以对话的知识系统</h1>
      <p>上传资料、自动切片、向量召回、流式问答，一套轻量但完整的 RAG 工作台。</p>
      <div class="intro-pills">
        <span>知识库管理</span>
        <span>AI 聊天</span>
        <span>模型切换</span>
      </div>
    </section>

    <section class="login-card">
      <h2>进入工作台</h2>
      <p class="card-desc">登录后开始管理知识库和模型配置。</p>

      <el-tabs v-model="activeTab" class="login-tabs">
        <el-tab-pane label="登录" name="login">
          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="rules"
            label-position="top"
            @submit.prevent="handleLogin"
          >
            <el-form-item label="用户名" prop="username">
              <el-input v-model="loginForm.username" placeholder="请输入用户名" size="large" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                show-password
                @keyup.enter="handleLogin"
              />
            </el-form-item>
            <el-button type="primary" :loading="loading" size="large" class="login-btn" @click="handleLogin">
              {{ loading ? '登录中...' : '登录' }}
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form
            ref="registerFormRef"
            :model="registerForm"
            :rules="registerRules"
            label-position="top"
            @submit.prevent="handleRegister"
          >
            <el-form-item label="用户名" prop="username">
              <el-input v-model="registerForm.username" placeholder="请输入用户名，3-50 个字符" size="large" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="registerForm.password"
                type="password"
                placeholder="请输入密码，至少 6 个字符"
                size="large"
                show-password
              />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="registerForm.nickname" placeholder="请输入昵称，可选" size="large" />
            </el-form-item>
            <el-button type="primary" :loading="loading" size="large" class="login-btn" @click="handleRegister">
              {{ loading ? '注册中...' : '注册并进入' }}
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../stores/user'
import { login, register } from '../../api/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('login')
const loading = ref(false)
const loginFormRef = ref(null)
const registerFormRef = ref(null)

const loginForm = reactive({
  username: '',
  password: ''
})

const registerForm = reactive({
  username: '',
  password: '',
  nickname: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度在 3 到 50 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少 6 个字符', trigger: 'blur' }
  ]
}

// 登录账号
async function handleLogin() {
  try {
    // 第1步：校验登录表单
    await loginFormRef.value.validate()
    // 第2步：进入加载状态
    loading.value = true
    // 第3步：调用登录接口
    const res = await login(loginForm)
    // 第4步：保存用户信息
    userStore.setUserInfo(res.data)
    // 第5步：提示成功并进入知识库页
    ElMessage.success('登录成功')
    router.push('/kb')
  } catch (error) {
    // 第6步：登录失败时记录错误
    console.error(error)
  } finally {
    // 第7步：关闭加载状态
    loading.value = false
  }
}

// 注册账号
async function handleRegister() {
  try {
    // 第1步：校验注册表单
    await registerFormRef.value.validate()
    // 第2步：进入加载状态
    loading.value = true
    // 第3步：调用注册接口
    const res = await register(registerForm)
    // 第4步：保存用户信息
    userStore.setUserInfo(res.data)
    // 第5步：提示成功并进入知识库页
    ElMessage.success('注册成功')
    router.push('/kb')
  } catch (error) {
    // 第6步：注册失败时记录错误
    console.error(error)
  } finally {
    // 第7步：关闭加载状态
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(320px, 1.1fr) minmax(360px, 520px);
  gap: 44px;
  align-items: center;
  padding: 56px;
  overflow: hidden;
  background:
    radial-gradient(circle at 15% 15%, rgba(21, 94, 239, 0.2), transparent 30%),
    radial-gradient(circle at 90% 80%, rgba(15, 159, 122, 0.18), transparent 28%),
    linear-gradient(135deg, #0b1220, #172033 48%, #0d1f1a);
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(10px);
  opacity: 0.7;
}

.orb-a {
  width: 260px;
  height: 260px;
  left: 8%;
  top: 8%;
  background: rgba(21, 94, 239, 0.2);
}

.orb-b {
  width: 340px;
  height: 340px;
  right: -80px;
  bottom: -100px;
  background: rgba(15, 159, 122, 0.24);
}

.login-intro,
.login-card {
  position: relative;
  z-index: 1;
}

.brand-mark {
  width: 70px;
  height: 70px;
  display: grid;
  place-items: center;
  margin-bottom: 24px;
  border-radius: 24px;
  color: #ffffff;
  background: linear-gradient(135deg, #155eef, #0f9f7a);
  box-shadow: 0 22px 46px rgba(21, 94, 239, 0.32);
}

.brand-mark svg {
  width: 36px;
  height: 36px;
}

.login-intro .eyebrow {
  color: #d0e7ff;
  background: rgba(255, 255, 255, 0.1);
}

.login-intro h1 {
  max-width: 760px;
  color: #ffffff;
  font-size: clamp(42px, 6vw, 76px);
  line-height: 1.02;
  font-weight: 900;
  letter-spacing: -2px;
}

.login-intro p {
  max-width: 620px;
  margin-top: 22px;
  color: rgba(255, 255, 255, 0.68);
  font-size: 18px;
  line-height: 1.8;
}

.intro-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 28px;
}

.intro-pills span {
  padding: 10px 14px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 999px;
  color: rgba(255, 255, 255, 0.78);
  background: rgba(255, 255, 255, 0.08);
}

.login-card {
  padding: 34px;
  border: 1px solid rgba(255, 255, 255, 0.62);
  border-radius: 32px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.24);
  backdrop-filter: blur(24px);
}

.login-card h2 {
  font-size: 30px;
  font-weight: 900;
}

.card-desc {
  margin-top: 8px;
  color: var(--muted-color);
}

.login-tabs {
  margin-top: 22px;
}

.login-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.login-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  font-weight: 800;
}

.login-btn {
  width: 100%;
  height: 48px;
  margin-top: 4px;
}

@media (max-width: 980px) {
  .login-page {
    grid-template-columns: 1fr;
    padding: 28px;
  }
}
</style>
