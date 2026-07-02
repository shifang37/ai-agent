<template>
  <ChatRoom
    app-title="AI 超级智能体"
    app-desc="自主规划任务、调用工具、分步执行，助你搞定复杂问题 🤖"
    app-icon="🤖"
    accent="#6366f1"
    accent-soft="#e8e9ff"
    placeholder="描述你想完成的任务…"
    :show-session-id="false"
    :chunk-separator="'\n'"
    :build-url="buildUrl"
    :suggestions="suggestions"
  />
</template>

<script setup>
import ChatRoom from '../components/ChatRoom.vue'
import { buildManusSseUrl } from '../api/chat'

// 超级智能体接口只需要 message，不需要 chatId
const buildUrl = (message) => buildManusSseUrl(message)

const suggestions = [
  '帮我搜索并整理今天的科技新闻',
  '制定一份 3 天的杭州旅游攻略',
  '分析一下最近热门的编程语言趋势'
]
</script>
