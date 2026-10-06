<template>
  <div class="users-page">
    <el-card shadow="never">
      <template #header>
        <span>用户管理</span>
        <el-button type="primary" style="float: right" @click="openForm()">添加用户</el-button>
      </template>

      <div class="table-wrapper">
        <el-table :data="users" v-loading="loading" stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="username" label="用户名" min-width="100" />
          <el-table-column prop="realName" label="姓名" width="90">
            <template #default="{ row }">{{ row.realName || '--' }}</template>
          </el-table-column>
          <el-table-column prop="roleName" label="角色" width="120">
            <template #default="{ row }">
              <el-tag :type="row.roleId === 1 ? 'danger' : 'primary'" size="small">
                {{ row.roleId === 1 ? '管理员' : '工作人员' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="phone" label="电话" width="120">
            <template #default="{ row }">{{ row.phone || '--' }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" min-width="150" />
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="openForm(row)">编辑</el-button>
              <el-button type="warning" link @click="openResetPwd(row)">重置密码</el-button>
              <el-popconfirm title="确认删除该用户？" @confirm="deleteUserItem(row.id)">
                <template #reference>
                  <el-button type="danger" link :disabled="row.id === 1">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-pagination
        style="margin-top: 16px; justify-content: flex-end"
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadData"
      />
    </el-card>

    <!-- Add/Edit Dialog -->
    <el-dialog v-model="formVisible" :title="isEdit ? '编辑用户' : '添加用户'" :width="dialogWidth">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select v-model="form.roleId" style="width: 100%">
            <el-option label="系统管理员" :value="1" />
            <el-option label="巡查工作人员" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- Reset Password Dialog -->
    <el-dialog v-model="resetPwdVisible" title="重置密码" :width="dialogWidth">
      <el-form label-width="80px">
        <el-form-item label="用户">{{ resetPwdUser?.username }}</el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetPwdVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResetPwd">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUsers, addUser, updateUser, deleteUser, resetPassword } from '../api'

const loading = ref(false)
const users = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10 })

const formVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const submitLoading = ref(false)
const currentEditId = ref(null)

const resetPwdVisible = ref(false)
const resetPwdUser = ref(null)
const newPassword = ref('')
const dialogWidth = window.innerWidth < 768 ? '95%' : '500px'

const form = reactive({ username: '', password: '', realName: '', roleId: 2, phone: '', status: 1 })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  roleId: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getUsers(query)
    users.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const openForm = (row) => {
  isEdit.value = !!row
  if (row) {
    currentEditId.value = row.id
    Object.assign(form, { username: row.username, password: '', realName: row.realName, roleId: row.roleId, phone: row.phone, status: row.status })
  } else {
    Object.assign(form, { username: '', password: '', realName: '', roleId: 2, phone: '', status: 1 })
  }
  formVisible.value = true
}

const submitForm = async () => {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateUser(currentEditId.value, form)
      ElMessage.success('更新成功')
    } else {
      await addUser(form)
      ElMessage.success('添加成功')
    }
    formVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

const deleteUserItem = async (id) => {
  await deleteUser(id)
  ElMessage.success('删除成功')
  loadData()
}

const openResetPwd = (row) => {
  resetPwdUser.value = row
  newPassword.value = ''
  resetPwdVisible.value = true
}

const submitResetPwd = async () => {
  if (!newPassword.value) {
    ElMessage.warning('请输入新密码')
    return
  }
  await resetPassword(resetPwdUser.value.id, { newPassword: newPassword.value })
  ElMessage.success('密码已重置')
  resetPwdVisible.value = false
}

onMounted(loadData)
</script>

<style scoped>
.table-wrapper {
  overflow-x: auto;
}
</style>
