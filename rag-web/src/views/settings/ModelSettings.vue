<template>
  <MainLayout>
    <div class="settings-page page-shell">
      <section class="page-hero">
        <div>
          <span class="eyebrow">Model Providers</span>
          <h1 class="section-title">模型供应商配置</h1>
          <p class="section-desc">统一管理大模型 API 地址、密钥和模型名称，一键切换当前聊天使用的模型。</p>
        </div>
        <el-button type="primary" size="large" @click="openDialog()">
          <el-icon><Plus /></el-icon>
          新增供应商
        </el-button>
      </section>

      <section class="provider-panel glass-panel">
        <el-table :data="providerList" stripe class="provider-table">
          <el-table-column prop="name" label="供应商名称" width="150" />
          <el-table-column prop="model" label="模型名称" width="190" />
          <el-table-column prop="baseUrl" label="API 地址" min-width="260" show-overflow-tooltip />
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isActive === 1 ? 'success' : 'info'" size="small">
                {{ row.isActive === 1 ? '使用中' : '未启用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="240" align="center">
            <template #default="{ row }">
              <el-button v-if="row.isActive !== 1" type="primary" link @click="handleActivate(row)">
                激活
              </el-button>
              <el-button type="primary" link @click="openDialog(row)">
                编辑
              </el-button>
              <el-button type="danger" link :disabled="row.isActive === 1" @click="handleDelete(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <el-dialog
        v-model="dialogVisible"
        :title="editingId ? '编辑供应商' : '新增供应商'"
        width="520px"
        destroy-on-close
      >
        <el-form :model="form" label-width="96px">
          <el-form-item label="供应商名称">
            <el-input v-model="form.name" placeholder="例如：GPT、DeepSeek、Mimo" />
          </el-form-item>
          <el-form-item label="API 地址">
            <el-input v-model="form.baseUrl" placeholder="例如：https://api.openai.com" />
          </el-form-item>
          <el-form-item label="API 密钥">
            <el-input v-model="form.apiKey" type="password" show-password placeholder="请输入 API Key" />
          </el-form-item>
          <el-form-item label="模型名称">
            <el-input v-model="form.model" placeholder="例如：gpt-5.4-mini、deepseek-chat" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </template>
      </el-dialog>
    </div>
  </MainLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import {
  getModelProviderList,
  createModelProvider,
  updateModelProvider,
  deleteModelProvider,
  activateModelProvider
} from '../../api/modelProvider'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import MainLayout from '../../layouts/MainLayout.vue'

const providerList = ref([])
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const form = ref({
  name: '',
  baseUrl: '',
  apiKey: '',
  model: ''
})

onMounted(() => {
  loadList()
})

// 加载供应商列表
async function loadList() {
  try {
    // 第1步：请求后端列表接口
    const res = await getModelProviderList()
    // 第2步：保存供应商列表
    providerList.value = res.data || []
  } catch (error) {
    // 第3步：加载失败时记录错误
    console.error(error)
  }
}

// 打开新增或编辑弹窗
function openDialog(row) {
  // 第1步：编辑时回填当前行数据
  if (row) {
    editingId.value = row.id
    form.value = {
      name: row.name,
      baseUrl: row.baseUrl,
      apiKey: row.apiKey,
      model: row.model
    }
  } else {
    // 第2步：新增时清空表单
    editingId.value = null
    form.value = { name: '', baseUrl: '', apiKey: '', model: '' }
  }
  // 第3步：打开弹窗
  dialogVisible.value = true
}

// 保存供应商配置
async function handleSave() {
  // 第1步：校验必填字段
  if (!form.value.name || !form.value.baseUrl || !form.value.apiKey || !form.value.model) {
    ElMessage.warning('请填写所有字段')
    return
  }
  // 第2步：进入保存状态
  saving.value = true
  try {
    // 第3步：根据是否有编辑编号决定新增或修改
    if (editingId.value) {
      await updateModelProvider(editingId.value, form.value)
      ElMessage.success('修改成功')
    } else {
      await createModelProvider(form.value)
      ElMessage.success('新增成功')
    }
    // 第4步：关闭弹窗并刷新列表
    dialogVisible.value = false
    loadList()
  } catch (error) {
    // 第5步：保存失败时提示错误
    console.error(error)
    ElMessage.error('操作失败')
  } finally {
    // 第6步：关闭保存状态
    saving.value = false
  }
}

// 激活供应商
async function handleActivate(row) {
  try {
    // 第1步：调用激活接口
    await activateModelProvider(row.id)
    // 第2步：提示切换成功
    ElMessage.success(`已切换到 ${row.name}`)
    // 第3步：刷新列表
    loadList()
  } catch (error) {
    // 第4步：切换失败时提示错误
    console.error(error)
    ElMessage.error('切换失败')
  }
}

// 删除供应商
async function handleDelete(row) {
  try {
    // 第1步：确认是否删除
    await ElMessageBox.confirm(`确定要删除供应商「${row.name}」吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    // 第2步：取消删除时直接返回
    return
  }
  try {
    // 第3步：调用删除接口
    await deleteModelProvider(row.id)
    // 第4步：提示成功并刷新列表
    ElMessage.success('已删除')
    loadList()
  } catch (error) {
    // 第5步：删除失败时提示错误
    console.error(error)
    ElMessage.error('删除失败')
  }
}
</script>

<style scoped>
.provider-panel {
  padding: 22px;
}

.provider-table {
  width: 100%;
}
</style>
