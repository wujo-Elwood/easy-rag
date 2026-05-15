<template>
  <MainLayout>
    <div class="chat-page page-shell">
      <aside class="chat-sidebar">
        <section class="sidebar-panel">
          <h2>对话设置</h2>
          <p>选择知识库后，回答会优先引用已入库文档。</p>
          <el-select
            v-model="selectedKbId"
            class="side-control"
            placeholder="选择知识库"
            clearable
            filterable
          >
            <el-option
              v-for="kb in kbList"
              :key="kb.id"
              :label="kb.name"
              :value="kb.id"
            />
          </el-select>
          <el-select
            v-model="activeProviderId"
            class="side-control"
            placeholder="选择模型"
            filterable
            @change="handleSwitchProvider"
          >
            <el-option
              v-for="provider in providerList"
              :key="provider.id"
              :label="`${provider.name} / ${provider.model}`"
              :value="provider.id"
            />
          </el-select>
          <div class="mode-card">
            <span>当前模式</span>
            <strong>{{ selectedKbId ? '知识库问答' : '通用对话' }}</strong>
            <p>{{ selectedKbId ? '先检索相关文档片段，再组织回答。' : '不检索知识库，直接调用大模型回答。' }}</p>
            <small>模型：{{ activeProviderName }}</small>
          </div>
          <el-button class="side-button" @click="startNewSession">
            <el-icon><Refresh /></el-icon>
            新对话
          </el-button>
          <el-button class="side-button" :disabled="messages.length === 0" @click="exportChat">
            <el-icon><Download /></el-icon>
            导出对话
          </el-button>
        </section>

        <section class="sidebar-panel">
          <h2>召回测试</h2>
          <p>输入问题，查看知识库召回了哪些文本块。</p>
          <el-input
            v-model="recallQuery"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 4 }"
            placeholder="输入测试问题"
          />
          <el-button
            class="side-button primary"
            :loading="recallLoading"
            :disabled="!recallQuery.trim() || !selectedKbId"
            @click="handleRecallTest"
          >
            测试召回
          </el-button>
          <div v-if="recallResults.length > 0" class="recall-results">
            <div v-for="item in recallResults" :key="item.chunkId" class="recall-item">
              <div class="recall-item-header">
                <el-tag size="small" type="info">#{{ item.rank }}</el-tag>
                <span>{{ item.source }}</span>
              </div>
              <div class="recall-item-content">{{ item.content }}</div>
            </div>
          </div>
        </section>

        <section class="sidebar-panel history-panel">
          <h2>历史对话</h2>
          <div v-if="sessionList.length === 0" class="empty-history">暂无历史对话</div>
          <div
            v-for="session in sessionList"
            :key="session.id"
            class="history-item"
            :class="{ active: session.id === sessionId }"
          >
            <button class="history-item-main" @click="loadSession(session.id)">
              <span>{{ session.title }}</span>
              <small>{{ formatSessionTime(session.updatedAt) }}</small>
            </button>
            <el-icon class="history-item-delete" title="删除对话" @click.stop="handleDeleteSession(session)">
              <Delete />
            </el-icon>
          </div>
        </section>

        <section class="sidebar-panel">
          <h2>快捷问题</h2>
          <button
            v-for="question in quickQuestions"
            :key="question"
            class="quick-question"
            @click="useQuickQuestion(question)"
          >
            {{ question }}
          </button>
        </section>
      </aside>

      <section class="chat-main">
        <div class="chat-header">
          <div>
            <span class="eyebrow">AI Chat</span>
            <h1>智能问答</h1>
            <p>支持流式输出、知识库召回、历史对话和答案反馈。</p>
          </div>
          <el-tag :type="selectedKbId ? 'success' : 'info'" round>
            {{ selectedKbName }}
          </el-tag>
        </div>

        <div ref="messagesRef" class="chat-messages">
          <div v-if="messages.length === 0 && !loading" class="empty-chat">
            <el-icon><ChatDotRound /></el-icon>
            <h2>开始一次清晰的问答</h2>
            <p>可以先选择知识库，也可以直接向大模型提问。</p>
          </div>

          <article
            v-for="msg in messages"
            :key="msg.id"
            class="message-item"
            :class="msg.role"
          >
            <div class="message-avatar">
              <el-icon v-if="msg.role === 'user'"><User /></el-icon>
              <el-icon v-else><Monitor /></el-icon>
            </div>
            <div class="message-content">
              <div class="message-meta">
                <span>{{ msg.role === 'user' ? '你' : 'AI 助手' }}</span>
                <span v-if="msg._streaming">{{ msg.statusText || '生成中' }}</span>
              </div>
              <div v-if="msg._streaming && !msg.content" class="message-text loading-card">
                <span class="spinner"></span>
                <span>{{ msg.statusText || '正在思考并生成回答' }}</span>
              </div>
              <div v-else-if="msg._streaming" class="message-text streaming-text">
                {{ msg.content }}
              </div>
              <div v-else class="message-text" v-html="renderMarkdown(msg.content)" />
              <div v-if="msg.role === 'assistant' && !msg._streaming && msg.id" class="feedback-bar">
                <el-button
                  size="small"
                  :type="msg._feedback === 1 ? 'success' : 'default'"
                  text
                  @click="handleFeedback(msg, 1)"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="feedback-icon">
                    <path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14z"/>
                    <path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/>
                  </svg>
                  有帮助
                </el-button>
                <el-button
                  size="small"
                  :type="msg._feedback === 0 ? 'danger' : 'default'"
                  text
                  @click="handleFeedback(msg, 0)"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="feedback-icon">
                    <path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3H10z"/>
                    <path d="M17 2h3a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-3"/>
                  </svg>
                  无帮助
                </el-button>
              </div>
            </div>
          </article>

          <article v-if="waitingForResponse" class="message-item assistant">
            <div class="message-avatar">
              <el-icon><Monitor /></el-icon>
            </div>
            <div class="message-content">
              <div class="message-meta">
                <span>AI 助手</span>
                <span>生成中</span>
              </div>
              <div class="message-text loading-card">
                <span class="spinner"></span>
                <span>模型正在思考，请稍等...</span>
              </div>
            </div>
          </article>
        </div>

        <div class="chat-input-panel">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 7 }"
            placeholder="输入你的问题，Enter 发送，Shift + Enter 换行"
            @keydown.enter.exact="handleEnter"
          />
          <el-button
            type="primary"
            class="send-button"
            :loading="loading"
            :disabled="!inputMessage.trim()"
            @click="sendMessage"
          >
            <el-icon><Promotion /></el-icon>
            发送
          </el-button>
        </div>
      </section>
    </div>
  </MainLayout>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getKbList } from '../../api/kb'
import { getChatHistory, deleteSession, recallTest, submitFeedback } from '../../api/chat'
import { getModelProviderList, activateModelProvider } from '../../api/modelProvider'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  Delete,
  Download,
  Monitor,
  Promotion,
  Refresh,
  User
} from '@element-plus/icons-vue'
import MainLayout from '../../layouts/MainLayout.vue'
import MarkdownIt from 'markdown-it'

const route = useRoute()
const md = new MarkdownIt({ breaks: true, linkify: true })

const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const kbList = ref([])
const selectedKbId = ref(null)
const sessionId = ref(null)
const sessionList = ref([])
const messagesRef = ref(null)
const providerList = ref([])
const activeProviderId = ref(null)
const abortController = ref(null)
const waitingForResponse = ref(false)
const recallQuery = ref('')
const recallLoading = ref(false)
const recallResults = ref([])
let pollingTimer = null

const sessionStorageKey = 'rag_chat_sessions'

const quickQuestions = [
  '请总结这个知识库的核心内容',
  '有哪些需要注意的规则或流程？',
  '把相关内容整理成三条要点'
]

const selectedKbName = computed(() => {
  const kb = kbList.value.find(item => item.id === selectedKbId.value)
  return kb ? kb.name : '通用对话'
})

const activeProviderName = computed(() => {
  const provider = providerList.value.find(item => item.id === activeProviderId.value)
  return provider ? `${provider.name} / ${provider.model}` : '未选择模型'
})

onMounted(() => {
  loadSessionList()
  loadKbList()
  restoreKbFromQuery()
  loadProviders()
  restoreCurrentSession()
})

onBeforeUnmount(() => {
  if (sessionId.value) {
    localStorage.setItem('rag_current_session', String(sessionId.value))
  }
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
  stopPolling()
})

watch(messages, () => {
  scrollToBottom()
}, { deep: true })

// 加载知识库列表
async function loadKbList() {
  try {
    // 第1步：请求知识库列表
    const res = await getKbList()
    // 第2步：保存知识库数据
    kbList.value = res.data || []
    // 第3步：根据地址参数恢复选中知识库
    restoreKbFromQuery()
  } catch (error) {
    // 第4步：加载失败时记录错误
    console.error(error)
  }
}

// 加载模型供应商列表
async function loadProviders() {
  try {
    // 第1步：请求模型供应商列表
    const res = await getModelProviderList()
    // 第2步：保存供应商数据
    providerList.value = res.data || []
    // 第3步：找到当前激活的供应商
    const active = providerList.value.find(provider => provider.isActive === 1)
    if (active) {
      activeProviderId.value = active.id
    }
  } catch (error) {
    // 第4步：加载失败时记录错误
    console.error(error)
  }
}

// 切换当前模型供应商
async function handleSwitchProvider(providerId) {
  try {
    // 第1步：调用激活接口
    await activateModelProvider(providerId)
    // 第2步：更新本地激活编号
    activeProviderId.value = providerId
    // 第3步：提示切换成功
    const provider = providerList.value.find(item => item.id === providerId)
    ElMessage.success(`已切换到 ${provider.name}`)
  } catch (error) {
    // 第4步：切换失败时提示错误
    console.error(error)
    ElMessage.error('切换失败')
  }
}

// 根据地址参数恢复知识库
function restoreKbFromQuery() {
  // 第1步：读取地址栏中的知识库编号
  const queryKbId = route.query.kbId
  // 第2步：存在编号时选中对应知识库
  if (queryKbId) {
    selectedKbId.value = Number(queryKbId)
  }
}

// 加载本地会话列表
function loadSessionList() {
  try {
    // 第1步：读取浏览器本地缓存
    const rawSessions = localStorage.getItem(sessionStorageKey)
    // 第2步：解析会话列表
    sessionList.value = rawSessions ? JSON.parse(rawSessions) : []
  } catch {
    // 第3步：解析失败时使用空列表
    sessionList.value = []
  }
}

// 保存本地会话列表
function saveSessionList() {
  // 第1步：把会话列表写入浏览器本地缓存
  localStorage.setItem(sessionStorageKey, JSON.stringify(sessionList.value))
}

// 确保当前存在会话
function createSessionIfNeeded() {
  // 第1步：已经有会话时直接返回
  if (sessionId.value) {
    return
  }
  // 第2步：有历史会话时加载最新会话
  if (sessionList.value.length > 0) {
    loadSession(sessionList.value[0].id)
    return
  }
  // 第3步：没有历史会话时创建新会话
  startNewSession()
}

// 恢复当前会话
async function restoreCurrentSession() {
  // 第1步：读取页面刷新前保存的会话编号
  const savedSessionId = localStorage.getItem('rag_current_session')
  // 第2步：有保存会话时加载并检查是否需要轮询
  if (savedSessionId) {
    localStorage.removeItem('rag_current_session')
    await loadSessionAndPollIfNeeded(Number(savedSessionId))
    return
  }
  // 第3步：没有保存会话时创建或加载默认会话
  createSessionIfNeeded()
}

// 加载会话并检查轮询
async function loadSessionAndPollIfNeeded(targetSessionId) {
  // 第1步：设置当前会话编号
  sessionId.value = targetSessionId
  // 第2步：重新加载消息
  await reloadMessages(targetSessionId)
  // 第3步：滚动到底部
  scrollToBottom()
  // 第4步：检查是否需要等待后端回答
  checkAndStartPolling()
}

// 重新加载会话消息
async function reloadMessages(targetSessionId) {
  try {
    // 第1步：请求后端历史消息
    const res = await getChatHistory(targetSessionId)
    // 第2步：转换成前端消息结构
    messages.value = (res.data || []).map(item => ({
      id: item.id || createMessageId(),
      role: item.role,
      content: item.content || ''
    }))
  } catch (error) {
    // 第3步：加载失败时记录错误
    console.error(error)
  }
}

// 检查是否需要轮询等待回答
function checkAndStartPolling() {
  // 第1步：没有消息时关闭等待状态
  if (messages.value.length === 0) {
    waitingForResponse.value = false
    return
  }
  // 第2步：最后一条是用户消息时说明可能还在生成
  const lastMsg = messages.value[messages.value.length - 1]
  if (lastMsg.role === 'user') {
    waitingForResponse.value = true
    loading.value = true
    startPolling()
    return
  }
  // 第3步：最后一条不是用户消息时停止等待
  waitingForResponse.value = false
  loading.value = false
  stopPolling()
}

// 启动历史消息轮询
function startPolling() {
  // 第1步：先清理旧轮询
  stopPolling()
  // 第2步：定时查询后端历史消息
  pollingTimer = setInterval(async () => {
    if (!sessionId.value) {
      stopPolling()
      return
    }
    try {
      const res = await getChatHistory(sessionId.value)
      const serverMessages = (res.data || []).map(item => ({
        id: item.id || createMessageId(),
        role: item.role,
        content: item.content || ''
      }))
      if (serverMessages.length > messages.value.length) {
        messages.value = serverMessages
        waitingForResponse.value = false
        loading.value = false
        stopPolling()
        scrollToBottom()
      }
    } catch (error) {
      console.error('Polling error:', error)
    }
  }, 2000)
}

// 停止历史消息轮询
function stopPolling() {
  // 第1步：存在轮询时清理定时器
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

// 新建会话
function startNewSession() {
  // 第1步：生成新的会话编号
  sessionId.value = Date.now()
  // 第2步：清空当前消息
  messages.value = []
  // 第3步：保存会话列表
  upsertSession('新对话')
  // 第4步：滚动到底部
  scrollToBottom()
}

// 新增或更新会话摘要
function upsertSession(title) {
  // 第1步：查找当前会话是否已经存在
  const existSession = sessionList.value.find(item => item.id === sessionId.value)
  // 第2步：存在时更新标题和时间
  if (existSession) {
    existSession.title = title || existSession.title
    existSession.updatedAt = Date.now()
  } else {
    // 第3步：不存在时插入到列表顶部
    sessionList.value.unshift({ id: sessionId.value, title: title || '新对话', updatedAt: Date.now() })
  }
  // 第4步：最多保留 30 个会话
  sessionList.value = sessionList.value.slice(0, 30)
  // 第5步：保存到本地缓存
  saveSessionList()
}

// 加载指定历史会话
async function loadSession(targetSessionId) {
  // 第1步：生成中不允许切换会话
  if (loading.value) {
    ElMessage.warning('当前回答还在生成中，请稍后再切换对话')
    return
  }
  // 第2步：设置当前会话编号并清空消息
  sessionId.value = targetSessionId
  messages.value = []
  try {
    // 第3步：加载后端历史消息
    const res = await getChatHistory(targetSessionId)
    // 第4步：转换消息结构
    messages.value = (res.data || []).map(item => ({
      id: item.id || createMessageId(),
      role: item.role,
      content: item.content || ''
    }))
  } catch (error) {
    // 第5步：加载失败时提示错误
    console.error(error)
    ElMessage.error('历史对话加载失败')
  }
}

// 删除历史会话
async function handleDeleteSession(session) {
  // 第1步：生成中不允许删除会话
  if (loading.value) {
    ElMessage.warning('当前回答还在生成中，请稍后再操作')
    return
  }
  try {
    // 第2步：确认删除
    await ElMessageBox.confirm('确定要删除这个对话吗？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  // 第3步：从本地会话列表移除
  sessionList.value = sessionList.value.filter(item => item.id !== session.id)
  saveSessionList()
  // 第4步：如果删除的是当前会话就创建新会话
  if (session.id === sessionId.value) {
    sessionId.value = null
    messages.value = []
    createSessionIfNeeded()
  }
  // 第5步：通知后端删除历史消息
  try {
    await deleteSession(session.id)
  } catch (error) {
    console.error(error)
  }
  // 第6步：提示删除成功
  ElMessage.success('对话已删除')
}

// 使用快捷问题
function useQuickQuestion(question) {
  // 第1步：把快捷问题填入输入框
  inputMessage.value = question
}

// 处理 Enter 发送
function handleEnter(e) {
  // 第1步：阻止 textarea 默认换行
  e.preventDefault()
  // 第2步：发送消息
  sendMessage()
}

// 发送聊天消息
async function sendMessage() {
  // 第1步：空内容或正在加载时不发送
  if (!inputMessage.value.trim() || loading.value) {
    return
  }
  // 第2步：确保有当前会话
  createSessionIfNeeded()
  // 第3步：读取并清空用户输入
  const userMessage = inputMessage.value.trim()
  inputMessage.value = ''
  // 第4步：插入用户消息
  messages.value.push({ id: createMessageId(), role: 'user', content: userMessage })
  // 第5步：更新会话标题
  upsertSession(userMessage.slice(0, 24))
  // 第6步：插入助手占位消息
  loading.value = true
  waitingForResponse.value = false
  const assistantMessage = {
    id: createMessageId(),
    role: 'assistant',
    content: '',
    statusText: '正在连接大模型',
    _streaming: true
  }
  messages.value.push(assistantMessage)
  try {
    // 第7步：创建流式请求
    const response = await createStreamRequest(userMessage)
    // 第8步：校验响应是否可读
    if (!response.ok || !response.body) {
      throw new Error('聊天服务暂时不可用')
    }
    // 第9步：读取流式响应
    await readStream(response, assistantMessage.id)
  } catch (error) {
    // 第10步：请求失败时显示错误消息
    if (error.name === 'AbortError') {
      return
    }
    console.error(error)
    updateAssistantMessage(assistantMessage.id, { content: '发送失败，请稍后重试。' })
    ElMessage.error(error.message || '发送失败，请重试')
  } finally {
    // 第11步：结束流式状态
    updateAssistantMessage(assistantMessage.id, { _streaming: false, statusText: '' })
    loading.value = false
    abortController.value = null
  }
}

// 创建流式聊天请求
function createStreamRequest(userMessage) {
  // 第1步：创建可取消请求控制器
  const controller = new AbortController()
  abortController.value = controller
  // 第2步：读取登录令牌
  const token = localStorage.getItem('token')
  // 第3步：调用后端流式接口
  return fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ message: userMessage, kbId: selectedKbId.value, sessionId: sessionId.value }),
    signal: controller.signal
  })
}

// 读取流式响应
async function readStream(response, assistantMessageId) {
  // 第1步：创建流读取器和文本解码器
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  // 第2步：循环读取后端推送的数据块
  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    buffer = await consumeSseBuffer(buffer, assistantMessageId)
  }
  // 第3步：处理最后残留的数据
  if (buffer.trim()) {
    await appendSsePart(buffer, assistantMessageId)
  }
}

// 消费 SSE 缓存
async function consumeSseBuffer(buffer, assistantMessageId) {
  // 第1步：按 SSE 空行分隔事件
  const parts = buffer.replace(/\r\n/g, '\n').split('\n\n')
  // 第2步：保留最后一个可能不完整的事件
  const rest = parts.pop() || ''
  // 第3步：逐个处理完整事件
  for (const part of parts) {
    await appendSsePart(part, assistantMessageId)
  }
  // 第4步：返回未完成片段
  return rest
}

// 追加单个 SSE 事件
async function appendSsePart(part, assistantMessageId) {
  // 第1步：读取事件名和事件数据
  const eventName = getSseEventName(part)
  const text = getSseDataText(part)
  // 第2步：状态事件只更新状态文案
  if (eventName === 'status') {
    updateAssistantMessage(assistantMessageId, { statusText: text || '正在处理' })
    await waitForRenderFrame()
    return
  }
  // 第3步：连接和结束事件不追加正文
  if (eventName === 'open' || eventName === 'done' || text === '[DONE]') {
    return
  }
  // 第4步：错误事件追加错误内容
  if (eventName === 'error') {
    appendAssistantContent(assistantMessageId, text || '聊天服务异常')
    await waitForRenderFrame()
    return
  }
  // 第5步：普通消息事件追加正文
  if (text) {
    appendAssistantContent(assistantMessageId, text)
    await waitForRenderFrame()
  }
}

// 更新助手消息
function updateAssistantMessage(messageId, patchData) {
  // 第1步：通过重建数组触发 Vue 重新渲染
  messages.value = messages.value.map(item =>
    item.id === messageId ? { ...item, ...patchData } : item
  )
}

// 追加助手消息内容
function appendAssistantContent(messageId, text) {
  // 第1步：空文本直接跳过
  if (!text) {
    return
  }
  // 第2步：追加内容并触发重新渲染
  messages.value = messages.value.map(item =>
    item.id === messageId
      ? { ...item, content: `${item.content || ''}${text}`, statusText: '生成中' }
      : item
  )
}

// 等待浏览器完成一帧渲染
async function waitForRenderFrame() {
  // 第1步：等待 Vue 更新 DOM
  await nextTick()
  // 第2步：等待浏览器下一帧
  await new Promise(resolve => requestAnimationFrame(resolve))
  // 第3步：让出宏任务，避免批量数据一次性刷出
  await new Promise(resolve => window.setTimeout(resolve, 0))
}

// 获取 SSE 事件名
function getSseEventName(part) {
  // 第1步：查找 event 行
  const line = part.split('\n').find(item => item.startsWith('event:'))
  // 第2步：没有 event 行时默认按 message 处理
  return line ? line.substring(6).trim() : 'message'
}

// 获取 SSE 数据文本
function getSseDataText(part) {
  // 第1步：读取所有 data 行
  return part
    .split('\n')
    .filter(item => item.startsWith('data:'))
    .map(item => item.substring(5).replace(/^ /, ''))
    .join('\n')
}

// 渲染 Markdown 内容
function renderMarkdown(content) {
  // 第1步：空内容返回空字符串
  if (!content) {
    return ''
  }
  // 第2步：高亮来源标签
  const highlighted = content.replace(
    /\[来源:\s*([^\]]+)\]/g,
    '<span class="source-tag">来源: $1</span>'
  )
  // 第3步：渲染 Markdown
  return md.render(highlighted)
}

// 滚动到底部
function scrollToBottom() {
  // 第1步：等待 DOM 更新后滚动
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

// 创建前端消息编号
function createMessageId() {
  // 第1步：用时间戳和随机数生成临时编号
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

// 格式化会话时间
function formatSessionTime(value) {
  // 第1步：空值直接返回空字符串
  if (!value) {
    return ''
  }
  // 第2步：按本地时间格式展示
  return new Date(value).toLocaleString()
}

// 执行召回测试
async function handleRecallTest() {
  // 第1步：没有问题或没有知识库时直接返回
  if (!recallQuery.value.trim() || !selectedKbId.value) {
    return
  }
  // 第2步：清空旧结果并进入加载状态
  recallLoading.value = true
  recallResults.value = []
  try {
    // 第3步：调用召回测试接口
    const res = await recallTest(recallQuery.value, selectedKbId.value)
    // 第4步：保存召回结果
    recallResults.value = res.data || []
    // 第5步：没有结果时提示用户
    if (recallResults.value.length === 0) {
      ElMessage.info('未召回任何文本块')
    }
  } catch (error) {
    // 第6步：测试失败时提示错误
    console.error(error)
    ElMessage.error('召回测试失败')
  } finally {
    // 第7步：关闭加载状态
    recallLoading.value = false
  }
}

// 提交答案反馈
async function handleFeedback(msg, helpful) {
  try {
    // 第1步：调用反馈接口
    await submitFeedback(msg.id, helpful)
    // 第2步：更新当前消息反馈状态
    msg._feedback = helpful
    // 第3步：提示反馈成功
    ElMessage.success(helpful === 1 ? '感谢反馈' : '感谢反馈，我们会改进')
  } catch (error) {
    // 第4步：提交失败时提示错误
    console.error(error)
    ElMessage.error('反馈提交失败')
  }
}

// 导出当前对话
function exportChat() {
  // 第1步：没有消息时直接返回
  if (messages.value.length === 0) {
    return
  }
  // 第2步：组装 Markdown 文本
  let text = '# 对话导出\n\n'
  for (const msg of messages.value) {
    const role = msg.role === 'user' ? '用户' : 'AI 助手'
    text += `## ${role}\n\n${msg.content}\n\n---\n\n`
  }
  // 第3步：创建下载文件
  const blob = new Blob([text], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `对话导出_${new Date().toLocaleDateString()}.md`
  a.click()
  // 第4步：释放临时地址并提示成功
  URL.revokeObjectURL(url)
  ElMessage.success('导出成功')
}
</script>

<style scoped>
.chat-page {
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  gap: 20px;
  height: calc(100vh - 130px);
  min-height: 720px;
  overflow: hidden;
}

.chat-sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
  padding-right: 4px;
}

.sidebar-panel,
.chat-main {
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 26px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: var(--shadow-soft);
  backdrop-filter: blur(18px);
}

.sidebar-panel {
  padding: 20px;
}

.sidebar-panel h2 {
  font-size: 18px;
  font-weight: 900;
  color: var(--ink-color);
}

.sidebar-panel p {
  margin-top: 8px;
  color: var(--muted-color);
  line-height: 1.7;
}

.side-control {
  width: 100%;
  margin-top: 14px;
}

.mode-card {
  margin-top: 14px;
  padding: 16px;
  border-radius: 20px;
  color: #ffffff;
  background: linear-gradient(135deg, #101828, #155eef);
}

.mode-card span,
.mode-card small {
  color: rgba(255, 255, 255, 0.7);
}

.mode-card strong {
  display: block;
  margin-top: 8px;
  font-size: 18px;
}

.mode-card p {
  color: rgba(255, 255, 255, 0.76);
}

.side-button {
  width: 100%;
  margin-top: 12px;
}

.side-button.primary {
  color: #ffffff;
  border: none;
  background: linear-gradient(135deg, #155eef, #0f9f7a);
}

.recall-results {
  max-height: 320px;
  margin-top: 12px;
  overflow-y: auto;
}

.recall-item {
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid var(--line-color);
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.9);
}

.recall-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: var(--muted-color);
  font-size: 12px;
}

.recall-item-content {
  max-height: 110px;
  overflow: hidden;
  color: var(--ink-color);
  line-height: 1.7;
}

.empty-history {
  margin-top: 12px;
  color: var(--muted-color);
}

.history-item {
  display: flex;
  align-items: center;
  margin-top: 10px;
  border: 1px solid var(--line-color);
  border-radius: 18px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.74);
}

.history-item.active,
.history-item:hover {
  border-color: rgba(21, 94, 239, 0.38);
  background: rgba(21, 94, 239, 0.07);
}

.history-item-main {
  flex: 1;
  min-width: 0;
  padding: 12px 14px;
  border: none;
  color: var(--ink-color);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.history-item-main span,
.history-item-main small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-item-main span {
  font-weight: 800;
}

.history-item-main small {
  margin-top: 4px;
  color: var(--muted-color);
  font-size: 12px;
}

.history-item-delete {
  width: 42px;
  display: grid;
  place-items: center;
  color: var(--muted-color);
  cursor: pointer;
}

.history-item-delete:hover {
  color: var(--danger-color);
}

.quick-question {
  width: 100%;
  margin-top: 10px;
  padding: 12px 14px;
  border: 1px solid var(--line-color);
  border-radius: 18px;
  color: var(--ink-color);
  background: rgba(255, 255, 255, 0.74);
  text-align: left;
  cursor: pointer;
  font-weight: 700;
}

.quick-question:hover {
  color: var(--primary-color);
  border-color: rgba(21, 94, 239, 0.38);
}

.chat-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
  padding: 26px;
  border-bottom: 1px solid var(--line-color);
}

.chat-header h1 {
  font-size: 34px;
  line-height: 1.1;
  font-weight: 900;
}

.chat-header p {
  margin-top: 10px;
  color: var(--muted-color);
}

.chat-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 24px;
  background:
    radial-gradient(circle at 8% 0%, rgba(21, 94, 239, 0.08), transparent 24%),
    rgba(248, 250, 252, 0.62);
}

.empty-chat {
  min-height: 360px;
  display: grid;
  place-items: center;
  align-content: center;
  text-align: center;
  color: var(--muted-color);
}

.empty-chat .el-icon {
  margin-bottom: 18px;
  color: var(--primary-color);
  font-size: 58px;
}

.empty-chat h2 {
  margin-bottom: 8px;
  color: var(--ink-color);
  font-size: 26px;
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 16px;
  color: #ffffff;
}

.user .message-avatar {
  background: linear-gradient(135deg, #155eef, #123f96);
}

.assistant .message-avatar {
  background: linear-gradient(135deg, #0f9f7a, #08785c);
}

.message-content {
  max-width: min(76%, 840px);
}

.message-meta {
  display: flex;
  gap: 10px;
  margin-bottom: 7px;
  color: var(--muted-color);
  font-size: 12px;
  font-weight: 800;
}

.user .message-meta {
  justify-content: flex-end;
}

.message-text {
  padding: 15px 18px;
  border: 1px solid var(--line-color);
  border-radius: 22px;
  color: var(--ink-color);
  background: rgba(255, 255, 255, 0.9);
  line-height: 1.8;
  word-break: break-word;
  box-shadow: 0 10px 28px rgba(16, 24, 40, 0.06);
}

.user .message-text {
  color: #ffffff;
  border-color: transparent;
  background: linear-gradient(135deg, #155eef, #0f9f7a);
}

.streaming-text {
  white-space: pre-wrap;
}

.message-text :deep(p) {
  margin: 0 0 10px;
}

.message-text :deep(p:last-child) {
  margin-bottom: 0;
}

.message-text :deep(ul),
.message-text :deep(ol) {
  padding-left: 20px;
}

.message-text :deep(code) {
  padding: 2px 6px;
  border-radius: 8px;
  background: rgba(21, 94, 239, 0.09);
}

.message-text :deep(.source-tag) {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  color: var(--primary-dark);
  background: rgba(21, 94, 239, 0.1);
  font-size: 12px;
  font-weight: 800;
}

.loading-card {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--muted-color);
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(21, 94, 239, 0.18);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.feedback-bar {
  display: flex;
  gap: 4px;
  margin-top: 8px;
}

.feedback-icon {
  width: 15px;
  height: 15px;
  margin-right: 4px;
  vertical-align: middle;
}

.chat-input-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 118px;
  gap: 14px;
  align-items: end;
  padding: 18px;
  border-top: 1px solid var(--line-color);
  background: rgba(255, 255, 255, 0.88);
}

.send-button {
  min-height: 72px;
}

@media (max-width: 1020px) {
  .chat-page {
    grid-template-columns: 1fr;
    height: auto;
    overflow: visible;
  }

  .chat-main {
    min-height: 720px;
  }
}

@media (max-width: 640px) {
  .chat-header,
  .chat-input-panel {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .message-content {
    max-width: calc(100% - 54px);
  }
}
</style>
