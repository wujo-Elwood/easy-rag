<template>
  <MainLayout>
    <div class="file-page page-shell">
      <section class="page-hero">
        <div class="title-wrap">
          <el-button class="back-button" text @click="goBack">
            <el-icon><ArrowLeft /></el-icon>
          </el-button>
          <div>
            <span class="eyebrow">File Pipeline</span>
            <h1 class="section-title">{{ kbName || '知识库文件' }}</h1>
            <p class="section-desc">
              上传 PDF、DOC、DOCX、TXT、XLSX 或 Markdown，系统会自动解析、切片并写入向量库。
            </p>
          </div>
        </div>
        <el-button type="primary" size="large" @click="goToChat">
          <el-icon><ChatLineRound /></el-icon>
          去提问
        </el-button>
      </section>

      <section class="process-strip glass-panel">
        <div class="process-step">
          <span class="step-index">1</span>
          <div>
            <strong>上传文件</strong>
            <p>保存原始资料</p>
          </div>
        </div>
        <div class="process-line"></div>
        <div class="process-step">
          <span class="step-index">2</span>
          <div>
            <strong>解析切片</strong>
            <p>提取文本内容</p>
          </div>
        </div>
        <div class="process-line"></div>
        <div class="process-step">
          <span class="step-index">3</span>
          <div>
            <strong>向量入库</strong>
            <p>完成后可问答</p>
          </div>
        </div>
      </section>

      <section class="file-workbench">
        <div class="upload-panel glass-panel">
          <el-upload
            drag
            :action="uploadAction"
            :headers="uploadHeaders"
            :data="uploadData"
            :on-success="handleUploadSuccess"
            :on-error="handleUploadError"
            :before-upload="beforeUpload"
            :show-file-list="false"
            accept=".pdf,.doc,.docx,.txt,.xlsx,.md"
          >
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="upload-title">拖拽文件到这里，或点击上传</div>
            <div class="upload-tip">支持 PDF、DOC、DOCX、TXT、XLSX、Markdown，单个文件不超过 50MB</div>
          </el-upload>
        </div>

        <div class="stat-panel">
          <div class="stat-item">
            <span>文件总数</span>
            <strong>{{ fileStats.total }}</strong>
          </div>
          <div class="stat-item">
            <span>处理中</span>
            <strong>{{ fileStats.processing }}</strong>
          </div>
          <div class="stat-item">
            <span>可问答</span>
            <strong>{{ fileStats.completed }}</strong>
          </div>
        </div>
      </section>

      <section class="file-list-panel glass-panel">
        <div class="panel-head">
          <div>
            <h2>文件列表</h2>
            <p>文件完成处理后，会立刻参与知识库召回。</p>
          </div>
          <el-button :loading="loading" @click="loadFileList">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>

        <el-empty v-if="!loading && fileList.length === 0" description="暂无文件，请先上传文档" />
        <el-table v-else v-loading="loading" :data="fileList" style="width: 100%">
          <el-table-column prop="fileName" label="文件名称" min-width="260">
            <template #default="{ row }">
              <div class="file-name-cell">
                <el-icon><Document /></el-icon>
                <span>{{ row.fileName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="fileType" label="类型" width="110">
            <template #default="{ row }">
              <el-tag>{{ getFileType(row.fileType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="fileSize" label="大小" width="120">
            <template #default="{ row }">
              {{ formatSize(row.fileSize) }}
            </template>
          </el-table-column>
          <el-table-column prop="status" label="处理状态" width="140">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)" round>
                {{ getStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="上传时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <el-button
                type="primary"
                text
                :disabled="row.status === 'PROCESSING' || row.status === 'UPLOADED'"
                @click="handleReprocess(row)"
              >
                重新处理
              </el-button>
              <el-button type="danger" text @click="handleDelete(row.id)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </MainLayout>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getFileList, deleteFile, reprocessFile } from '../../api/file'
import { getKbList } from '../../api/kb'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  ChatLineRound,
  Document,
  Refresh,
  UploadFilled
} from '@element-plus/icons-vue'
import MainLayout from '../../layouts/MainLayout.vue'

const route = useRoute()
const router = useRouter()

const kbId = route.params.kbId
const kbName = ref('')
const fileList = ref([])
const loading = ref(false)
const pollingTimer = ref(null)

const uploadAction = `/api/file/upload`
const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${localStorage.getItem('token')}`
}))
const uploadData = computed(() => ({
  kbId: kbId
}))

const fileStats = computed(() => {
  return fileList.value.reduce((stats, file) => {
    stats.total += 1
    if (file.status === 'COMPLETED') {
      stats.completed += 1
    }
    if (file.status === 'PROCESSING' || file.status === 'UPLOADED') {
      stats.processing += 1
    }
    return stats
  }, { total: 0, processing: 0, completed: 0 })
})

onMounted(() => {
  loadKbInfo()
  loadFileList()
})

onBeforeUnmount(() => {
  stopPolling()
})

// 加载知识库名称
async function loadKbInfo() {
  try {
    // 第1步：查询知识库列表
    const res = await getKbList()
    // 第2步：找到当前知识库
    const kb = res.data?.find(item => item.id === Number(kbId))
    // 第3步：保存知识库名称
    if (kb) {
      kbName.value = kb.name
    }
  } catch (error) {
    // 第4步：查询失败时记录错误
    console.error(error)
  }
}

// 加载文件列表
async function loadFileList() {
  try {
    // 第1步：打开加载状态
    loading.value = true
    // 第2步：查询当前知识库的文件
    const res = await getFileList(kbId)
    // 第3步：保存文件列表
    fileList.value = res.data || []
    // 第4步：根据处理状态决定是否轮询
    updatePolling()
  } catch (error) {
    // 第5步：加载失败时记录错误
    console.error(error)
  } finally {
    // 第6步：关闭加载状态
    loading.value = false
  }
}

// 上传前校验文件
function beforeUpload(file) {
  // 第1步：定义允许上传的文件类型
  const allowedTypes = [
    'application/pdf',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'text/plain',
    'text/markdown',
    'text/x-markdown'
  ]
  // 第2步：兼容浏览器识别不到 MIME 类型的情况
  const allowedExts = ['.pdf', '.doc', '.docx', '.txt', '.xlsx', '.md']
  const ext = file.name ? file.name.substring(file.name.lastIndexOf('.')).toLowerCase() : ''
  // 第3步：校验文件格式
  if (!allowedTypes.includes(file.type) && !allowedExts.includes(ext)) {
    ElMessage.error('只支持 PDF、DOC、DOCX、TXT、XLSX、Markdown 格式的文件')
    return false
  }
  // 第4步：校验文件大小
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 50MB')
    return false
  }
  // 第5步：校验通过允许上传
  return true
}

// 处理上传成功
function handleUploadSuccess(response) {
  // 第1步：后端返回失败时提示错误
  if (response?.code !== 200) {
    ElMessage.error(response?.message || '上传失败')
    return
  }
  // 第2步：提示上传成功
  ElMessage.success('上传成功，正在解析入库')
  // 第3步：刷新文件列表
  loadFileList()
}

// 处理上传失败
function handleUploadError() {
  // 第1步：提示上传失败
  ElMessage.error('上传失败')
}

// 重新处理文件
async function handleReprocess(row) {
  try {
    // 第1步：确认重新处理
    await ElMessageBox.confirm(
      `确定要重新处理「${row.fileName}」吗？旧的切片和向量会被清除并重新生成。`,
      '重新处理确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    // 第2步：调用重新处理接口
    await reprocessFile(row.id)
    // 第3步：提示并刷新列表
    ElMessage.success('已开始重新处理')
    loadFileList()
  } catch (error) {
    // 第4步：取消操作不提示错误
    if (error !== 'cancel') {
      console.error(error)
      ElMessage.error('重新处理失败')
    }
  }
}

// 删除文件
async function handleDelete(id) {
  try {
    // 第1步：确认删除文件
    await ElMessageBox.confirm('确定要删除这个文件吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    // 第2步：调用删除接口
    await deleteFile(id)
    // 第3步：提示并刷新列表
    ElMessage.success('删除成功')
    loadFileList()
  } catch (error) {
    // 第4步：取消删除不提示错误
    if (error !== 'cancel') {
      console.error(error)
    }
  }
}

// 返回知识库列表
function goBack() {
  // 第1步：跳转到知识库页
  router.push('/kb')
}

// 跳转到聊天页
function goToChat() {
  // 第1步：带上当前知识库编号进入聊天页
  router.push({ path: '/chat', query: { kbId } })
}

// 更新轮询状态
function updatePolling() {
  // 第1步：判断是否存在处理中文件
  const hasProcessing = fileList.value.some(file => file.status === 'PROCESSING' || file.status === 'UPLOADED')
  // 第2步：有处理中文件就启动轮询
  if (hasProcessing) {
    startPolling()
    return
  }
  // 第3步：没有处理中文件就停止轮询
  stopPolling()
}

// 启动文件状态轮询
function startPolling() {
  // 第1步：已有轮询时直接返回
  if (pollingTimer.value) {
    return
  }
  // 第2步：定时刷新文件列表
  pollingTimer.value = window.setInterval(() => {
    loadFileList()
  }, 3000)
}

// 停止文件状态轮询
function stopPolling() {
  // 第1步：没有轮询时直接返回
  if (!pollingTimer.value) {
    return
  }
  // 第2步：清理定时器
  window.clearInterval(pollingTimer.value)
  // 第3步：清空定时器编号
  pollingTimer.value = null
}

// 获取文件类型文案
function getFileType(type) {
  // 第1步：根据 MIME 类型映射显示名称
  if (type?.includes('pdf')) { return 'PDF' }
  if (type?.includes('msword')) { return 'DOC' }
  if (type?.includes('word')) { return 'DOCX' }
  if (type?.includes('spreadsheet') || type?.includes('excel')) { return 'XLSX' }
  if (type?.includes('markdown')) { return 'MD' }
  if (type?.includes('text')) { return 'TXT' }
  // 第2步：无法识别时显示原始类型
  return type
}

// 格式化文件大小
function formatSize(bytes) {
  // 第1步：空值显示 0 B
  if (!bytes) { return '0 B' }
  // 第2步：逐级换算单位
  const units = ['B', 'KB', 'MB', 'GB']
  let index = 0
  let size = bytes
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index++
  }
  // 第3步：返回带单位的文件大小
  return `${size.toFixed(1)} ${units[index]}`
}

// 获取文件状态标签类型
function getStatusType(status) {
  // 第1步：按后端状态映射 Element Plus 标签类型
  const types = { UPLOADED: 'info', PROCESSING: 'warning', COMPLETED: 'success', FAILED: 'danger' }
  // 第2步：未知状态默认显示 info
  return types[status] || 'info'
}

// 获取文件状态文案
function getStatusText(status) {
  // 第1步：按后端状态映射中文文案
  const texts = { UPLOADED: '已上传', PROCESSING: '处理中', COMPLETED: '已完成', FAILED: '处理失败' }
  // 第2步：未知状态显示原始状态
  return texts[status] || status
}

// 格式化日期
function formatDate(dateStr) {
  // 第1步：空日期直接返回空字符串
  if (!dateStr) { return '' }
  // 第2步：按中文日期时间显示
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}
</script>

<style scoped>
.title-wrap {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.back-button {
  margin-top: 34px;
}

.process-strip {
  display: grid;
  grid-template-columns: 1fr 80px 1fr 80px 1fr;
  align-items: center;
  padding: 22px;
  margin-bottom: 20px;
}

.process-step {
  display: flex;
  align-items: center;
  gap: 14px;
}

.process-step strong {
  display: block;
  font-size: 16px;
  color: var(--ink-color);
}

.process-step p {
  margin-top: 4px;
  color: var(--muted-color);
}

.step-index {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  color: #ffffff;
  background: linear-gradient(135deg, #155eef, #0f9f7a);
  font-weight: 900;
}

.process-line {
  height: 2px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(21, 94, 239, 0.3), rgba(15, 159, 122, 0.3));
}

.file-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(320px, 1fr);
  gap: 18px;
  margin-bottom: 20px;
}

.upload-panel {
  padding: 18px;
}

.upload-panel :deep(.el-upload-dragger) {
  padding: 42px 18px;
  border: 1px dashed rgba(21, 94, 239, 0.42);
  border-radius: 24px;
  background:
    radial-gradient(circle at 50% 0%, rgba(21, 94, 239, 0.12), transparent 34%),
    rgba(255, 255, 255, 0.72);
}

.upload-icon {
  color: var(--primary-color);
  font-size: 50px;
}

.upload-title {
  margin-top: 14px;
  color: var(--ink-color);
  font-size: 18px;
  font-weight: 900;
}

.upload-tip {
  margin-top: 8px;
  color: var(--muted-color);
}

.stat-panel {
  display: grid;
  gap: 14px;
}

.stat-item {
  padding: 22px;
  border-radius: 24px;
  color: #ffffff;
  background: linear-gradient(135deg, #101828, #344054);
  box-shadow: var(--shadow-soft);
}

.stat-item:nth-child(2) {
  background: linear-gradient(135deg, #b54708, #d97706);
}

.stat-item:nth-child(3) {
  background: linear-gradient(135deg, #08785c, #0f9f7a);
}

.stat-item span {
  color: rgba(255, 255, 255, 0.72);
}

.stat-item strong {
  display: block;
  margin-top: 8px;
  font-size: 34px;
  font-weight: 900;
}

.file-list-panel {
  padding: 22px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 18px;
}

.panel-head h2 {
  font-size: 22px;
  font-weight: 900;
}

.panel-head p {
  margin-top: 6px;
  color: var(--muted-color);
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-weight: 800;
}

.file-name-cell span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .page-hero,
  .panel-head {
    flex-direction: column;
    align-items: stretch;
  }

  .process-strip,
  .file-workbench {
    grid-template-columns: 1fr;
  }

  .process-line {
    display: none;
  }
}
</style>
