<script setup>
import { ref } from 'vue'
import { api } from '../api'
import { auth, saveAuth, clearAuth } from '../auth'

const props = defineProps({ hint: { type: String, default: '' } })
const emit = defineEmits(['done'])

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function login() {
  if (!username.value.trim() || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  error.value = ''
  loading.value = true
  try {
    const d = await api.login(username.value.trim(), password.value)
    saveAuth({
      token: d.token,
      threadId: d.threadId,
      username: d.username,
      expireAt: d.expireAt || 0,
      guest: false
    })
    emit('done')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function guest() {
  clearAuth()
  saveAuth({ username: '游客', guest: true })
  emit('done')
}
</script>

<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="login-logo">🤖</div>
      <h1>刘曙光 · AI Agent 工作台</h1>
      <p class="login-sub">登录后与 Multi-Agent 智能体对话</p>

      <div v-if="hint" class="login-hint">{{ hint }}</div>

      <label class="field">
        <span>用户名</span>
        <input v-model="username" type="text" autocomplete="username" @keyup.enter="login" />
      </label>
      <label class="field">
        <span>密码</span>
        <input v-model="password" type="password" autocomplete="current-password" @keyup.enter="login" />
      </label>

      <div v-if="error" class="login-error">{{ error }}</div>

      <button class="btn btn-primary btn-block" :disabled="loading" @click="login">
        {{ loading ? '登录中…' : '登 录' }}
      </button>
      <button class="btn btn-ghost btn-block" :disabled="loading" @click="guest">
        游客试用（不登录，限 5 次调用）
      </button>
    </div>
  </div>
</template>
