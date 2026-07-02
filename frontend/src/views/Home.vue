<template>
  <div class="home">
    <div class="home-inner">
      <!-- 顶部状态：后端连通性 -->
      <div class="topbar">
        <span class="status" :class="status">
          <span class="status-dot" />
          {{ statusText }}
        </span>
      </div>

      <!-- 标题 -->
      <header class="hero">
        <div class="hero-badge">AI Application Hub</div>
        <h1>AI 应用集合</h1>
        <p>选择一个应用，开始你的 AI 对话之旅</p>
      </header>

      <!-- 应用卡片 -->
      <div class="cards">
        <router-link to="/love" class="card love">
          <div class="card-icon">💕</div>
          <h2>AI 恋爱大师</h2>
          <p>倾诉你的情感困惑，获得贴心的恋爱建议与沟通话术。</p>
          <span class="card-tags">
            <em>多轮对话</em>
            <em>实时流式</em>
          </span>
          <span class="card-go">进入应用 →</span>
        </router-link>

        <router-link to="/manus" class="card manus">
          <div class="card-icon">🤖</div>
          <h2>AI 超级智能体</h2>
          <p>自主规划、调用工具、分步执行，帮你完成复杂任务。</p>
          <span class="card-tags">
            <em>自主规划</em>
            <em>工具调用</em>
          </span>
          <span class="card-go">进入应用 →</span>
        </router-link>
      </div>

      <footer class="home-footer">
        后端接口：<code>{{ apiBase }}</code>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { healthCheck } from '../api/chat'
import { API_BASE_URL } from '../config'

const apiBase = API_BASE_URL
const status = ref('checking') // checking | online | offline
const statusText = ref('正在检测后端服务…')

onMounted(async () => {
  try {
    await healthCheck()
    status.value = 'online'
    statusText.value = '后端服务已连接'
  } catch {
    status.value = 'offline'
    statusText.value = '后端未连接（请启动 localhost:8123）'
  }
})
</script>

<style scoped>
.home {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  background: radial-gradient(1200px 600px at 15% -10%, #ffe6ec 0%, transparent 55%),
    radial-gradient(1200px 600px at 85% 110%, #e8e9ff 0%, transparent 55%),
    var(--color-bg);
}
.home-inner {
  width: 100%;
  max-width: 920px;
}

.topbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 28px;
}
.status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-soft);
  background: var(--color-surface);
  padding: 8px 14px;
  border-radius: 20px;
  box-shadow: var(--shadow-sm);
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f59e0b;
}
.status.online .status-dot {
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.18);
}
.status.offline .status-dot {
  background: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.18);
}

.hero {
  text-align: center;
  margin-bottom: 42px;
}
.hero-badge {
  display: inline-block;
  font-size: 12px;
  letter-spacing: 2px;
  font-weight: 600;
  color: var(--manus);
  background: #fff;
  padding: 6px 14px;
  border-radius: 20px;
  box-shadow: var(--shadow-sm);
  margin-bottom: 18px;
}
.hero h1 {
  margin: 0 0 12px;
  font-size: clamp(32px, 6vw, 46px);
  font-weight: 800;
  letter-spacing: -1px;
  background: linear-gradient(120deg, #ff5a7a 0%, #6366f1 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.hero p {
  margin: 0;
  color: var(--color-text-soft);
  font-size: 16px;
}

.cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}
.card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 30px;
  background: var(--color-surface);
  border-radius: 22px;
  box-shadow: var(--shadow-md);
  border: 1px solid var(--color-border);
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 5px;
}
.card.love::before {
  background: linear-gradient(90deg, #ff5a7a, #ff9a8b);
}
.card.manus::before {
  background: linear-gradient(90deg, #6366f1, #8b5cf6);
}
.card:hover {
  transform: translateY(-6px);
  box-shadow: var(--shadow-lg);
}
.card-icon {
  font-size: 44px;
  width: 74px;
  height: 74px;
  display: grid;
  place-items: center;
  border-radius: 20px;
  margin-bottom: 18px;
}
.card.love .card-icon {
  background: var(--love-soft);
}
.card.manus .card-icon {
  background: var(--manus-soft);
}
.card h2 {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 700;
}
.card p {
  margin: 0 0 18px;
  color: var(--color-text-soft);
  font-size: 14px;
  line-height: 1.7;
  flex: 1;
}
.card-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}
.card-tags em {
  font-style: normal;
  font-size: 12px;
  color: var(--color-text-soft);
  background: var(--color-bg);
  padding: 5px 12px;
  border-radius: 14px;
}
.card-go {
  font-size: 15px;
  font-weight: 700;
}
.card.love .card-go {
  color: var(--love);
}
.card.manus .card-go {
  color: var(--manus);
}

.home-footer {
  margin-top: 34px;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-mute);
}
.home-footer code {
  background: var(--color-surface);
  padding: 3px 8px;
  border-radius: 6px;
  box-shadow: var(--shadow-sm);
}

@media (max-width: 640px) {
  .cards {
    grid-template-columns: 1fr;
  }
}
</style>
