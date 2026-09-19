<script setup>
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { api, ApiError } from '../api'
import { auth, isLoggedIn, clearAuth, saveAuth } from '../auth'
import { renderMd } from '../md'

const emit = defineEmits(['open-login'])

const agents = [
  { key: 'multi', label: 'Multi-Agent', hint: '主管分配客服/技术/数据专家，敏感操作需人工审批' }
]

// 常用语：点一下直接发送
const quickPhrases = ['你是谁']

// 每个 Agent 一份对话记录，持久化在 localStorage（服务端记忆按 threadId 一直都在）
const HIST_KEY = 'lg4j_chats'
const history = reactive(JSON.parse(localStorage.getItem(HIST_KEY) || '{}'))
for (const a of agents) {
  if (!Array.isArray(history[a.key])) history[a.key] = []
}
// 清理已下线 Agent 的历史记录
Object.keys(history).forEach((k) => {
  if (!agents.some((a) => a.key === k)) delete history[k]
})
function persist() {
  localStorage.setItem(HIST_KEY, JSON.stringify(history))
}

const activeKey = ref('multi')
const active = computed(() => agents.find((a) => a.key === activeKey.value))
const messages = computed(() => history[activeKey.value])

const input = ref('')
const sending = ref(false)
const pending = reactive({ multi: false }) // 是否有未处理的审批
const listEl = ref(null)

const inputDisabled = computed(() => sending.value || !!pending[activeKey.value])

onMounted(scrollBottom)

function switchAgent(key) {
  activeKey.value = key
  nextTick(scrollBottom)
}

function scrollBottom() {
  nextTick(() => {
    if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
  })
}

function push(role, text, extra = {}) {
  history[activeKey.value].push({ role, text, time: Date.now(), ...extra })
  persist()
  scrollBottom()
}

async function send(preset) {
  // 兼容 @click="send" 这类绑定会把事件对象当参数传入的情况
  const question = (typeof preset === 'string' ? preset : input.value).trim()
  if (!question || inputDisabled.value) return
  if (typeof preset !== 'string') input.value = ''
  push('user', question)
  sending.value = true
  scrollBottom()
  try {
    const body = isLoggedIn() ? { question, threadId: auth.threadId } : { question }
    handleAgentResponse(await api.multiAsk(body))
  } catch (e) {
    handleError(e)
  } finally {
    sending.value = false
    scrollBottom()
  }
}

function handleAgentResponse(resp) {
  if (resp.status === 'PENDING_APPROVAL') {
    pending[activeKey.value] = true
    push('approval', resp.message || 'Agent 请求调用工具，请确认', { threadId: resp.threadId })
  } else if (resp.status === 'REJECTED') {
    push('assistant', resp.message || '已拒绝本次操作')
  } else {
    push('assistant', resp.answer || '（空回答）')
  }
}

async function approve(approved) {
  const key = activeKey.value
  sending.value = true
  scrollBottom()
  try {
    const body = isLoggedIn() ? { threadId: auth.threadId, approved } : { approved }
    const resp = await api.multiApprove(body)
    pending[key] = false
    // 把最近一张未处理的审批卡片标记为已处理，按钮置为结果状态
    const card = [...history[key]].reverse().find((m) => m.role === 'approval' && m.resolved === undefined)
    if (card) card.resolved = approved
    persist()
    push('assistant', resp.status === 'COMPLETED' ? resp.answer : resp.message || '已处理')
  } catch (e) {
    handleError(e)
  } finally {
    sending.value = false
    scrollBottom()
  }
}

function handleError(e) {
  if (e instanceof ApiError && e.status === 401 && isLoggedIn()) {
    clearAuth()
    emit('open-login', '登录已过期或已失效，请重新登录')
    return
  }
  push('error', e.message || '未知错误')
}

async function logout() {
  try {
    if (isLoggedIn()) await api.logout(auth.token)
  } catch (e) {
    // 登出失败不阻塞，本地清掉即可
  }
  // 登出后停留在对话页，以游客身份继续使用
  clearAuth()
  saveAuth({ username: '游客', guest: true })
}

function clearChat() {
  history[activeKey.value] = []
  pending[activeKey.value] = false
  persist()
}

function formatTime(t) {
  return new Date(t).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <div class="chat-wrap">
    <header class="topbar">
      <div class="topbar-title">🤖 刘曙光 · AI Agent 工作台</div>
      <div class="topbar-right">
        <span class="badge" :class="isLoggedIn() ? 'badge-user' : 'badge-guest'">
          {{ isLoggedIn() ? auth.username : '游客模式 · 限 5 次' }}
        </span>
        <span v-if="isLoggedIn()" class="thread-id" :title="'永久 threadId：' + auth.threadId">
          thread: {{ auth.threadId.slice(0, 13) }}…
        </span>
        <button v-if="isLoggedIn()" class="btn btn-small" @click="logout">登出</button>
        <button v-else class="btn btn-small" @click="emit('open-login', '')">登录</button>
      </div>
    </header>

    <nav v-if="agents.length > 1" class="tabs">
      <button
        v-for="a in agents"
        :key="a.key"
        class="tab"
        :class="{ active: activeKey === a.key }"
        @click="switchAgent(a.key)"
      >
        {{ a.label }}
        <span v-if="pending[a.key]" class="tab-dot" title="有待处理的审批"></span>
      </button>
    </nav>

    <div class="chat-hint">{{ active.hint }}</div>

    <main ref="listEl" class="chat-list">
      <div v-if="messages.length === 0" class="chat-empty">
        没有对话记录，输入问题开始吧～<br />
        <small>试试："订单 ORD12345678 我要退款"（会触发人工审批）</small>
      </div>

      <div v-for="(m, i) in messages" :key="i" class="msg" :class="'msg-' + m.role">
        <div class="bubble">
          <template v-if="m.role === 'approval'">
            <div class="approval-text">⚠️ {{ m.text }}</div>
            <div v-if="m.resolved === undefined" class="approval-actions">
              <button class="btn btn-primary" :disabled="sending" @click="approve(true)">批准执行</button>
              <button class="btn btn-danger" :disabled="sending" @click="approve(false)">拒绝</button>
            </div>
            <div v-else class="approval-done">{{ m.resolved ? '✅ 已批准执行' : '❌ 已拒绝' }}</div>
          </template>
          <template v-else-if="m.role === 'assistant'">
            <div class="md-body" v-html="renderMd(m.text)"></div>
          </template>
          <template v-else>{{ m.text }}</template>
        </div>
        <div class="msg-time">{{ formatTime(m.time) }}</div>
      </div>

      <div v-if="sending" class="msg msg-assistant">
        <div class="bubble bubble-thinking"><span></span><span></span><span></span></div>
      </div>
    </main>

    <div class="quick-row">
      <button
        v-for="p in quickPhrases"
        :key="p"
        class="quick-chip"
        :disabled="inputDisabled"
        @click="send(p)"
      >
        💬 {{ p }}
      </button>
    </div>

    <footer class="input-bar">
      <textarea
        v-model="input"
        rows="1"
        :placeholder="inputDisabled && pending[activeKey]
          ? '请先处理上方的审批请求…'
          : (isLoggedIn() ? '输入问题，Enter 发送' : '游客模式：输入问题，Enter 发送')"
        :disabled="inputDisabled"
        @keydown.enter.exact.prevent="send()"
      ></textarea>
      <button class="btn btn-primary btn-send" :disabled="inputDisabled || !input.trim()" @click="send()">
        发送
      </button>
    </footer>
  </div>
</template>
