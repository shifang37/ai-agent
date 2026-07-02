<template>
  <ChatRoom
    app-title="AI 恋爱大师"
    app-desc="倾诉你的情感困惑，让 AI 恋爱大师为你答疑解惑 💕"
    app-icon="💕"
    accent="#ff5a7a"
    accent-soft="#ffe6ec"
    placeholder="说说你的恋爱烦恼吧…"
    :show-session-id="true"
    :build-url="buildUrl"
    :suggestions="suggestions"
  />
</template>

<script setup>
import ChatRoom from '../components/ChatRoom.vue'
import { buildLoveAppSseUrl } from '../api/chat'

// 恋爱大师需要 chatId 以区分不同会话
const buildUrl = (message, chatId) => buildLoveAppSseUrl(message, chatId)

const suggestions = [
  '和喜欢的人聊天总是很尴尬，怎么办？',
  '异地恋要如何维持感情？',
  '第一次约会去哪里比较好？',
  '怎么判断对方是不是喜欢我？'
]
</script>
