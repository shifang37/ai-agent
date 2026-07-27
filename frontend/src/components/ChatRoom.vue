<template>
  <div class="chat-room" :style="themeVars">
    <!-- 顶部栏 -->
    <header class="chat-header">
      <router-link to="/" class="back-btn" title="返回主页">
        <span class="back-arrow">‹</span>
      </router-link>

      <div class="header-title">
        <span class="header-icon">{{ appIcon }}</span>
        <div class="header-text">
          <h1>{{ appTitle }}</h1>
          <p>{{ appDesc }}</p>
        </div>
      </div>

      <div class="header-actions">
        <span v-if="showSessionId" class="session-badge" :title="'会话 ID：' + chatId">
          <span class="dot" />
          会话 {{ shortChatId }}
        </span>
        <button
          v-if="showSessionId"
          class="ghost-btn"
          type="button"
          title="开启一个全新的会话（清空聊天记录）"
          @click="newSession"
        >
          新会话
        </button>
      </div>
    </header>

    <!-- 聊天记录 -->
    <main ref="listRef" class="chat-body">
      <!-- 空状态 / 欢迎页 -->
      <div v-if="messages.length === 0" class="welcome">
        <div class="welcome-icon">{{ appIcon }}</div>
        <h2>{{ appTitle }}</h2>
        <p>{{ appDesc }}</p>
        <div v-if="suggestions.length" class="suggestions">
          <button
            v-for="(s, i) in suggestions"
            :key="i"
            class="suggestion-chip"
            type="button"
            @click="sendText(s)"
          >
            {{ s }}
          </button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div
        v-for="msg in messages"
        :key="msg.id"
        class="msg-row"
        :class="msg.role"
      >
        <div class="avatar" :class="msg.role">
          {{ msg.role === 'user' ? '我' : appIcon }}
        </div>

        <div class="bubble-wrap">
          <!-- 高危工具人工确认卡片 -->
          <div v-if="msg.type === 'confirm'" class="bubble ai confirm-card">
            <div class="confirm-title">⚠️ 高危操作待确认</div>
            <div class="confirm-desc">智能体请求执行以下高危工具，是否允许？</div>
            <div v-for="(tool, i) in msg.payload.tools" :key="i" class="confirm-tool">
              <div class="confirm-tool-name">{{ tool.name }}</div>
              <pre class="confirm-tool-args">{{ tool.arguments }}</pre>
            </div>
            <div v-if="msg.confirmState === 'pending'" class="confirm-actions">
              <button class="confirm-btn approve" type="button" @click="handleConfirm(msg, true)">批准执行</button>
              <button class="confirm-btn reject" type="button" @click="handleConfirm(msg, false)">拒绝</button>
            </div>
            <div v-else class="confirm-result" :class="msg.confirmState">
              {{ msg.confirmState === 'approved' ? '✅ 已批准，继续执行…' : '⛔ 已拒绝该操作' }}
            </div>
          </div>

          <template v-else>
          <div class="bubble" :class="[msg.role, { error: msg.status === 'error' }]">
            <!-- 流式中且暂无内容：显示打字动画 -->
            <span v-if="msg.role === 'ai' && msg.status === 'streaming' && !msg.content" class="typing">
              <i></i><i></i><i></i>
            </span>
            <template v-else>
              <span class="bubble-text">{{ msg.content }}</span>
              <!-- 正在生成时的闪烁光标 -->
              <span v-if="msg.status === 'streaming'" class="cursor">▋</span>
            </template>
          </div>

          <div class="bubble-meta">
            <span class="time">{{ msg.time }}</span>
            <button
              v-if="msg.role === 'ai' && msg.content && msg.status !== 'streaming'"
              class="copy-btn"
              type="button"
              title="复制"
              @click="copy(msg.content)"
            >
              复制
            </button>
          </div>
          </template>
        </div>
      </div>
    </main>

    <!-- 输入区 -->
    <footer class="chat-footer">
      <div class="input-box">
        <textarea
          v-model="input"
          :placeholder="placeholder"
          rows="1"
          ref="textareaRef"
          @keydown="onKeydown"
          @compositionstart="composing = true"
          @compositionend="composing = false"
          @input="autoGrow"
        />
        <button
          v-if="loading"
          class="stop-btn"
          type="button"
          @click="stop"
        >
          停止生成
        </button>
        <button
          v-else
          class="send-btn"
          type="button"
          :disabled="!canSend"
          @click="send"
        >
          发送
        </button>
      </div>
      <p class="hint">Enter 发送 · Shift + Enter 换行</p>
    </footer>
  </div>
</template>

<script setup>
import { computed, nextTick, onUnmounted, ref } from 'vue'
import { createSSEConnection } from '../utils/sse'
import { generateChatId, uid } from '../utils/id'
import { confirmManusToolCall } from '../api/chat'

const props = defineProps({
  appTitle: { type: String, required: true },
  appDesc: { type: String, default: '' },
  appIcon: { type: String, default: '🤖' },
  accent: { type: String, default: '#6366f1' },
  accentSoft: { type: String, default: '#e8e9ff' },
  placeholder: { type: String, default: '输入消息…' },
  // 是否展示会话 ID 及“新会话”按钮（恋爱大师需要，智能体不需要）
  showSessionId: { type: Boolean, default: false },
  // 多个数据块之间的连接符：恋爱大师逐 token 用 ''，智能体逐步骤用 '\n'
  chunkSeparator: { type: String, default: '' },
  // 由父组件提供的地址构造函数：(message, chatId) => url
  buildUrl: { type: Function, required: true },
  // 引导用的快捷提问
  suggestions: { type: Array, default: () => [] }
})

const messages = ref([])
const input = ref('')
const loading = ref(false)
const composing = ref(false)
const chatId = ref(generateChatId())

const listRef = ref(null)
const textareaRef = ref(null)
let currentConn = null

const themeVars = computed(() => ({
  '--accent': props.accent,
  '--accent-soft': props.accentSoft
}))

const shortChatId = computed(() => chatId.value.slice(0, 8))
const canSend = computed(() => input.value.trim().length > 0 && !loading.value)

function nowTime() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function scrollToBottom() {
  await nextTick()
  const el = listRef.value
  if (el) el.scrollTop = el.scrollHeight
}

function autoGrow() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    // 中文输入法组合输入中，回车用于选词，不应触发发送
    if (composing.value || e.isComposing) return
    e.preventDefault()
    send()
  }
}

function send() {
  const text = input.value.trim()
  if (!text || loading.value) return
  sendText(text)
}

function sendText(text) {
  if (loading.value) return
  const content = text.trim()
  if (!content) return

  // 追加用户消息
  messages.value.push({
    id: uid(),
    role: 'user',
    content,
    status: 'done',
    time: nowTime()
  })

  // 追加 AI 占位消息（流式填充）
  messages.value.push({
    id: uid(),
    role: 'ai',
    content: '',
    status: 'streaming',
    time: nowTime()
  })
  // 取回响应式代理对象，后续对它的修改才会触发视图更新
  const ai = messages.value[messages.value.length - 1]

  input.value = ''
  nextTick(autoGrow)
  loading.value = true
  scrollToBottom()

  const url = props.buildUrl(content, chatId.value)
  currentConn = createSSEConnection(url, {
    onMessage: (data) => {
      // 高危工具确认事件：弹出确认卡片而非拼接到正文
      if (data.startsWith('[CONFIRM]')) {
        try {
          const payload = JSON.parse(data.slice('[CONFIRM]'.length))
          messages.value.push({
            id: uid(),
            role: 'ai',
            type: 'confirm',
            payload,
            confirmState: 'pending',
            content: '',
            status: 'done',
            time: nowTime()
          })
          scrollToBottom()
          return
        } catch {
          // 解析失败则按普通文本处理
        }
      }
      // 逐块拼接：token 用 '' 连接，步骤用 '\n' 连接
      const sep = ai.content && props.chunkSeparator ? props.chunkSeparator : ''
      ai.content += sep + data
      scrollToBottom()
    },
    onDone: () => {
      ai.status = 'done'
      if (!ai.content) ai.content = '（未收到返回内容）'
      loading.value = false
      currentConn = null
      scrollToBottom()
    },
    onError: (err) => {
      ai.status = 'error'
      ai.content = ai.content || `⚠️ ${err.message}`
      loading.value = false
      currentConn = null
      scrollToBottom()
    }
  })
}

async function handleConfirm(msg, approved) {
  if (msg.confirmState !== 'pending') return
  msg.confirmState = approved ? 'approved' : 'rejected'
  try {
    await confirmManusToolCall(msg.payload.agentId, approved)
  } catch {
    // 提交失败恢复待确认状态，允许重试
    msg.confirmState = 'pending'
  }
}

function stop() {
  if (currentConn) {
    currentConn.close()
    currentConn = null
  }
  loading.value = false
}

function newSession() {
  stop()
  messages.value = []
  chatId.value = generateChatId()
}

async function copy(text) {
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    // 忽略复制失败（例如非 HTTPS 环境）
  }
}

onUnmounted(() => {
  if (currentConn) currentConn.close()
})
</script>

<style scoped>
.chat-room {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
}

/* ===== 顶部栏 ===== */
.chat-header {
  flex: none;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 20px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
  z-index: 2;
}
.back-btn {
  flex: none;
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  color: var(--color-text-soft);
  background: var(--color-bg);
  transition: background 0.15s, color 0.15s;
}
.back-btn:hover {
  background: var(--accent-soft);
  color: var(--accent);
}
.back-arrow {
  font-size: 26px;
  line-height: 1;
  margin-top: -2px;
}
.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1;
}
.header-icon {
  font-size: 26px;
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: var(--accent-soft);
}
.header-text {
  min-width: 0;
}
.header-text h1 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
}
.header-text p {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-mute);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.header-actions {
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
}
.session-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-soft);
  background: var(--color-bg);
  padding: 6px 10px;
  border-radius: 20px;
}
.session-badge .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.15);
}
.ghost-btn {
  font-size: 13px;
  color: var(--accent);
  background: var(--accent-soft);
  padding: 7px 14px;
  border-radius: 20px;
  font-weight: 600;
  transition: filter 0.15s;
}
.ghost-btn:hover {
  filter: brightness(0.96);
}

/* ===== 聊天记录 ===== */
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px clamp(12px, 6vw, 120px);
  display: flex;
  flex-direction: column;
  gap: 18px;
  scroll-behavior: smooth;
}

/* 欢迎页 */
.welcome {
  margin: auto;
  text-align: center;
  max-width: 560px;
  color: var(--color-text-soft);
}
.welcome-icon {
  font-size: 60px;
  margin-bottom: 10px;
  filter: drop-shadow(0 6px 16px rgba(0, 0, 0, 0.12));
}
.welcome h2 {
  margin: 6px 0;
  font-size: 22px;
  color: var(--color-text);
}
.welcome p {
  margin: 0 0 22px;
}
.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}
.suggestion-chip {
  font-size: 13px;
  color: var(--color-text);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  padding: 9px 16px;
  border-radius: 20px;
  box-shadow: var(--shadow-sm);
  transition: transform 0.12s, border-color 0.15s, color 0.15s;
}
.suggestion-chip:hover {
  transform: translateY(-2px);
  border-color: var(--accent);
  color: var(--accent);
}

/* 消息行 */
.msg-row {
  display: flex;
  gap: 12px;
  max-width: 78%;
  animation: pop 0.22s ease;
}
.msg-row.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}
.msg-row.ai {
  align-self: flex-start;
}
@keyframes pop {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.avatar {
  flex: none;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-size: 18px;
  font-weight: 600;
}
.avatar.ai {
  background: var(--accent-soft);
}
.avatar.user {
  background: var(--accent);
  color: #fff;
  font-size: 14px;
}

.bubble-wrap {
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.msg-row.user .bubble-wrap {
  align-items: flex-end;
}

.bubble {
  padding: 11px 15px;
  border-radius: 16px;
  font-size: 15px;
  line-height: 1.7;
  word-break: break-word;
  box-shadow: var(--shadow-sm);
}
.bubble-text {
  white-space: pre-wrap;
}
.bubble.ai {
  background: var(--color-surface);
  border-top-left-radius: 4px;
  color: var(--color-text);
}
.bubble.user {
  background: var(--accent);
  color: #fff;
  border-top-right-radius: 4px;
}
.bubble.error {
  background: #fff1f0;
  color: #cf1322;
}

/* 高危工具确认卡片 */
.confirm-card {
  border: 1.5px solid #f59e0b;
  background: #fffbeb;
  max-width: 100%;
}
.confirm-title {
  font-weight: 700;
  color: #b45309;
  margin-bottom: 4px;
}
.confirm-desc {
  font-size: 13px;
  color: var(--color-text-soft);
  margin-bottom: 8px;
}
.confirm-tool {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 8px 10px;
  margin-bottom: 8px;
}
.confirm-tool-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text);
}
.confirm-tool-args {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--color-text-soft);
  white-space: pre-wrap;
  word-break: break-all;
}
.confirm-actions {
  display: flex;
  gap: 10px;
  margin-top: 4px;
}
.confirm-btn {
  padding: 6px 18px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  transition: filter 0.15s;
}
.confirm-btn:hover {
  filter: brightness(1.05);
}
.confirm-btn.approve {
  background: #16a34a;
}
.confirm-btn.reject {
  background: #dc2626;
}
.confirm-result {
  font-size: 13px;
  font-weight: 600;
  margin-top: 4px;
}
.confirm-result.approved {
  color: #16a34a;
}
.confirm-result.rejected {
  color: #dc2626;
}

.bubble-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 5px;
  padding: 0 4px;
  height: 16px;
}
.time {
  font-size: 11px;
  color: var(--color-text-mute);
}
.copy-btn {
  font-size: 11px;
  color: var(--color-text-mute);
  opacity: 0;
  transition: opacity 0.15s, color 0.15s;
}
.msg-row:hover .copy-btn {
  opacity: 1;
}
.copy-btn:hover {
  color: var(--accent);
}

/* 打字动画 */
.typing {
  display: inline-flex;
  gap: 4px;
  padding: 3px 0;
}
.typing i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-text-mute);
  animation: blink 1.2s infinite ease-in-out;
}
.typing i:nth-child(2) {
  animation-delay: 0.2s;
}
.typing i:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes blink {
  0%,
  80%,
  100% {
    opacity: 0.25;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}
.cursor {
  display: inline-block;
  margin-left: 1px;
  color: var(--accent);
  animation: caret 1s steps(1) infinite;
}
@keyframes caret {
  50% {
    opacity: 0;
  }
}

/* ===== 输入区 ===== */
.chat-footer {
  flex: none;
  padding: 14px clamp(12px, 6vw, 120px) 18px;
  background: var(--color-surface);
  border-top: 1px solid var(--color-border);
}
.input-box {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  background: var(--color-bg);
  border: 1.5px solid var(--color-border);
  border-radius: 16px;
  padding: 8px 8px 8px 14px;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.input-box:focus-within {
  border-color: var(--accent);
  box-shadow: 0 0 0 4px var(--accent-soft);
}
.input-box textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  font-size: 15px;
  line-height: 1.6;
  max-height: 160px;
  padding: 6px 0;
  color: var(--color-text);
}
.send-btn,
.stop-btn {
  flex: none;
  height: 40px;
  padding: 0 22px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  transition: filter 0.15s, opacity 0.15s;
}
.send-btn {
  background: var(--accent);
}
.send-btn:hover:not(:disabled) {
  filter: brightness(1.05);
}
.send-btn:disabled {
  opacity: 0.45;
}
.stop-btn {
  background: #64748b;
}
.stop-btn:hover {
  filter: brightness(1.05);
}
.hint {
  margin: 8px 4px 0;
  font-size: 11px;
  color: var(--color-text-mute);
  text-align: center;
}

@media (max-width: 640px) {
  .msg-row {
    max-width: 90%;
  }
  .header-text p {
    display: none;
  }
}
</style>
