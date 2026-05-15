<template>
  <MainLayout>
    <div class="kb-page page-shell">
      <section class="page-hero kb-hero">
        <div>
          <span class="eyebrow">Knowledge Base</span>
          <h1 class="section-title">把文档变成可追问的知识库</h1>
          <p class="section-desc">
            上传资料后自动解析、切片、向量化，聊天时按知识库召回相关内容，让回答更贴近你的业务资料。
          </p>
        </div>
        <el-button type="primary" size="large" class="create-btn" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>
          新建知识库
        </el-button>
      </section>

      <section class="stats-grid">
        <div class="stat-card total-card">
          <span>知识库总数</span>
          <strong>{{ kbStats.total }}</strong>
          <small>已创建的知识集合</small>
        </div>
        <div class="stat-card ready-card">
          <span>可问答</span>
          <strong>{{ kbStats.ready }}</strong>
          <small>已有完成入库文件</small>
        </div>
        <div class="stat-card file-card">
          <span>文件总数</span>
          <strong>{{ kbStats.files }}</strong>
          <small>参与检索的资料</small>
        </div>
      </section>

      <section class="toolbar glass-panel">
        <div class="search-input-wrap">
          <el-icon><Search /></el-icon>
          <input
            v-model="searchText"
            type="text"
            class="search-input"
            placeholder="搜索知识库名称或描述"
          />
        </div>
        <el-button :loading="loading" @click="loadKbList">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </section>

      <section class="kb-content">
        <el-skeleton v-if="loading" :rows="5" animated />
        <div v-else-if="filteredKbList.length === 0" class="empty-state glass-panel">
          <div class="empty-icon">
            <el-icon><Collection /></el-icon>
          </div>
          <h3>还没有知识库</h3>
          <p>点击右上角“新建知识库”，先创建一个资料空间。</p>
        </div>
        <div v-else class="kb-grid">
          <article v-for="kb in filteredKbList" :key="kb.id" class="kb-card">
            <div class="card-top">
              <div class="card-icon">
                <el-icon><Collection /></el-icon>
              </div>
              <el-dropdown trigger="click">
                <button class="more-btn">
                  <el-icon><MoreFilled /></el-icon>
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="goToFile(kb.id)">管理文件</el-dropdown-item>
                    <el-dropdown-item divided @click="handleDelete(kb.id)">删除知识库</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>

            <h3>{{ kb.name }}</h3>
            <p class="card-desc">{{ kb.description || '暂无描述' }}</p>

            <div class="card-meta">
              <span>
                <el-icon><Document /></el-icon>
                {{ kb.fileCount || 0 }} 个文件
              </span>
              <span>
                <el-icon><Clock /></el-icon>
                {{ formatDate(kb.createTime) }}
              </span>
            </div>

            <div class="card-footer">
              <span class="status-badge" :class="getKbStatusClass(kb)">
                {{ getKbStatusText(kb) }}
              </span>
              <div class="card-actions">
                <el-button @click="goToFile(kb.id)">文件管理</el-button>
                <el-button type="primary" @click="goToChat(kb.id)">去提问</el-button>
              </div>
            </div>
          </article>
        </div>
      </section>

      <el-dialog v-model="dialogVisible" title="新建知识库" width="520px" class="create-dialog">
        <el-form ref="formRef" :model="form" :rules="formRules" label-position="top">
          <el-form-item label="名称" prop="name">
            <el-input v-model="form.name" placeholder="例如：员工制度库" size="large" />
          </el-form-item>
          <el-form-item label="描述" prop="description">
            <el-input
              v-model="form.description"
              type="textarea"
              rows="3"
              placeholder="简单说明这个知识库里会放哪些资料"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false" size="large">取消</el-button>
          <el-button type="primary" :loading="creating" @click="handleCreate" size="large">
            创建
          </el-button>
        </template>
      </el-dialog>
    </div>
  </MainLayout>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getKbList, createKb, deleteKb } from '../../api/kb'
import { getFileList } from '../../api/file'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock, Collection, Document, MoreFilled, Plus, Refresh, Search } from '@element-plus/icons-vue'
import MainLayout from '../../layouts/MainLayout.vue'

const router = useRouter()

const kbList = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const creating = ref(false)
const formRef = ref(null)
const searchText = ref('')

const form = reactive({
  name: '',
  description: ''
})

const formRules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }]
}

const filteredKbList = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  if (!keyword) {
    return kbList.value
  }
  return kbList.value.filter(item => {
    const name = item.name?.toLowerCase() || ''
    const description = item.description?.toLowerCase() || ''
    return name.includes(keyword) || description.includes(keyword)
  })
})

const kbStats = computed(() => {
  return kbList.value.reduce((stats, item) => {
    stats.total += 1
    stats.files += item.fileCount || 0
    if ((item.completedCount || 0) > 0) {
      stats.ready += 1
    }
    return stats
  }, { total: 0, ready: 0, files: 0 })
})

onMounted(() => {
  loadKbList()
})

// 加载知识库列表
async function loadKbList() {
  try {
    // 第1步：显示加载状态
    loading.value = true
    // 第2步：读取知识库列表
    const res = await getKbList()
    const list = res.data || []
    // 第3步：补充每个知识库的文件统计
    kbList.value = await Promise.all(list.map(loadKbFileStats))
  } catch (error) {
    // 第4步：加载失败时记录错误
    console.error(error)
  } finally {
    // 第5步：关闭加载状态
    loading.value = false
  }
}

// 加载单个知识库的文件统计
async function loadKbFileStats(kb) {
  try {
    // 第1步：查询当前知识库下的文件
    const res = await getFileList(kb.id)
    // 第2步：整理文件状态数量
    const files = res.data || []
    const completedCount = files.filter(f => f.status === 'COMPLETED').length
    const processingCount = files.filter(f => f.status === 'PROCESSING' || f.status === 'UPLOADED').length
    // 第3步：返回带统计信息的知识库对象
    return { ...kb, fileCount: files.length, completedCount, processingCount }
  } catch (error) {
    // 第4步：统计失败时使用默认数量
    console.error(error)
    return { ...kb, fileCount: 0, completedCount: 0, processingCount: 0 }
  }
}

// 打开新建知识库弹窗
function showCreateDialog() {
  // 第1步：清空表单
  form.name = ''
  form.description = ''
  // 第2步：打开弹窗
  dialogVisible.value = true
}

// 创建知识库
async function handleCreate() {
  try {
    // 第1步：校验表单
    await formRef.value.validate()
    // 第2步：提交创建请求
    creating.value = true
    await createKb(form)
    // 第3步：提示成功并刷新列表
    ElMessage.success('创建成功')
    dialogVisible.value = false
    loadKbList()
  } catch (error) {
    // 第4步：创建失败时记录错误
    console.error(error)
  } finally {
    // 第5步：关闭提交状态
    creating.value = false
  }
}

// 删除知识库
async function handleDelete(id) {
  try {
    // 第1步：确认是否删除
    await ElMessageBox.confirm('确定要删除这个知识库吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    // 第2步：调用删除接口
    await deleteKb(id)
    // 第3步：提示成功并刷新列表
    ElMessage.success('删除成功')
    loadKbList()
  } catch (error) {
    // 第4步：取消删除不提示错误
    if (error !== 'cancel') {
      console.error(error)
    }
  }
}

// 跳转到文件管理页
function goToFile(kbId) {
  // 第1步：带知识库编号进入文件页
  router.push(`/file/${kbId}`)
}

// 跳转到聊天页
function goToChat(kbId) {
  // 第1步：带知识库编号进入聊天页
  router.push({ path: '/chat', query: { kbId } })
}

// 获取知识库状态文案
function getKbStatusText(kb) {
  // 第1步：有文件正在处理时显示处理中
  if ((kb.processingCount || 0) > 0) {
    return '处理中'
  }
  // 第2步：有完成文件时显示可问答
  if ((kb.completedCount || 0) > 0) {
    return '可问答'
  }
  // 第3步：没有完成文件时显示待上传
  return '待上传'
}

// 获取知识库状态样式
function getKbStatusClass(kb) {
  // 第1步：处理中的知识库使用黄色状态
  if ((kb.processingCount || 0) > 0) {
    return 'processing'
  }
  // 第2步：可问答的知识库使用绿色状态
  if ((kb.completedCount || 0) > 0) {
    return 'ready'
  }
  // 第3步：待上传的知识库使用灰色状态
  return 'pending'
}

// 格式化日期
function formatDate(dateStr) {
  // 第1步：空日期直接返回空字符串
  if (!dateStr) {
    return ''
  }
  // 第2步：按中文日期格式显示
  return new Date(dateStr).toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.kb-hero .create-btn {
  min-width: 150px;
  height: 52px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
  margin-bottom: 18px;
}

.stat-card {
  position: relative;
  overflow: hidden;
  min-height: 142px;
  padding: 24px;
  border-radius: 26px;
  color: #ffffff;
  box-shadow: var(--shadow-card);
}

.stat-card::after {
  content: "";
  position: absolute;
  right: -40px;
  bottom: -50px;
  width: 150px;
  height: 150px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
}

.total-card {
  background: linear-gradient(135deg, #155eef, #123f96);
}

.ready-card {
  background: linear-gradient(135deg, #0f9f7a, #08785c);
}

.file-card {
  background: linear-gradient(135deg, #344054, #101828);
}

.stat-card span,
.stat-card small {
  position: relative;
  z-index: 1;
  display: block;
  color: rgba(255, 255, 255, 0.74);
}

.stat-card strong {
  position: relative;
  z-index: 1;
  display: block;
  margin: 12px 0 8px;
  font-size: 46px;
  line-height: 1;
  font-weight: 900;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  margin-bottom: 20px;
}

.search-input-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 48px;
  padding: 0 16px;
  border: 1px solid var(--line-color);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.88);
}

.search-input-wrap .el-icon {
  color: var(--subtle-color);
  font-size: 18px;
}

.search-input {
  width: 100%;
  border: none;
  outline: none;
  color: var(--ink-color);
  background: transparent;
  font-size: 15px;
}

.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
  gap: 18px;
}

.kb-card {
  position: relative;
  overflow: hidden;
  min-height: 300px;
  padding: 24px;
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: 28px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(255, 255, 255, 0.78)),
    radial-gradient(circle at 20% 0%, rgba(21, 94, 239, 0.15), transparent 34%);
  box-shadow: var(--shadow-soft);
  backdrop-filter: blur(18px);
  transition: all 0.24s ease;
}

.kb-card:hover {
  transform: translateY(-5px);
  box-shadow: var(--shadow-card);
}

.kb-card::before {
  content: "";
  position: absolute;
  inset: 0 0 auto;
  height: 6px;
  background: linear-gradient(90deg, #155eef, #0f9f7a);
}

.card-top,
.card-meta,
.card-footer,
.card-actions {
  display: flex;
  align-items: center;
}

.card-top {
  justify-content: space-between;
  margin-bottom: 24px;
}

.card-icon {
  width: 58px;
  height: 58px;
  display: grid;
  place-items: center;
  border-radius: 20px;
  color: #ffffff;
  font-size: 26px;
  background: linear-gradient(135deg, #155eef, #0f9f7a);
  box-shadow: var(--shadow-blue);
}

.more-btn {
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 14px;
  color: var(--muted-color);
  background: rgba(16, 24, 40, 0.05);
  cursor: pointer;
}

.kb-card h3 {
  margin-bottom: 10px;
  font-size: 22px;
  font-weight: 900;
  color: var(--ink-color);
}

.card-desc {
  min-height: 48px;
  color: var(--muted-color);
  line-height: 1.7;
}

.card-meta {
  gap: 16px;
  margin: 20px 0;
  color: var(--muted-color);
}

.card-meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.card-footer {
  justify-content: space-between;
  gap: 12px;
  padding-top: 18px;
  border-top: 1px solid var(--line-color);
}

.status-badge {
  display: inline-flex;
  padding: 7px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 900;
}

.status-badge.ready {
  color: #067647;
  background: rgba(6, 118, 71, 0.12);
}

.status-badge.processing {
  color: #b54708;
  background: rgba(181, 71, 8, 0.12);
}

.status-badge.pending {
  color: #475467;
  background: rgba(71, 84, 103, 0.12);
}

.card-actions {
  gap: 8px;
}

.empty-state {
  display: grid;
  place-items: center;
  min-height: 320px;
  padding: 40px;
  text-align: center;
}

.empty-icon {
  width: 80px;
  height: 80px;
  display: grid;
  place-items: center;
  margin-bottom: 16px;
  border-radius: 26px;
  color: var(--primary-color);
  font-size: 38px;
  background: rgba(21, 94, 239, 0.1);
}

.empty-state h3 {
  margin-bottom: 8px;
  font-size: 22px;
}

.empty-state p {
  color: var(--muted-color);
}

@media (max-width: 860px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .kb-grid {
    grid-template-columns: 1fr;
  }
}
</style>
