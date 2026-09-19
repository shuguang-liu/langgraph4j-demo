<script setup>
import { ref } from 'vue'
import { auth, isTokenExpired, clearAuth } from './auth'
import LoginPanel from './components/LoginPanel.vue'
import ChatPanel from './components/ChatPanel.vue'

// 默认进入对话页面：未登录时以游客身份使用（服务端按 IP 限次），点右上角"登录"再登录
let hint = ''
if (isTokenExpired()) {
  clearAuth()
}
const view = ref('chat')

function openLogin(message) {
  hint = message || ''
  view.value = 'login'
}
</script>

<template>
  <LoginPanel v-if="view === 'login'" :hint="hint" @done="view = 'chat'" />
  <ChatPanel v-else @open-login="openLogin" />
</template>
