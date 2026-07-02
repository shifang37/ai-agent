# AI 应用集合 · 前端

基于 **Vue 3 + Vite + Vue Router + Axios** 的前端项目，包含两个 AI 应用：

| 应用 | 说明 | 后端接口 | 是否需要 chatId |
| --- | --- | --- | --- |
| 💕 AI 恋爱大师 | 聊天室风格，SSE 实时流式对话 | `GET /ai/love_app/chat/sse` | ✅ 进入页面自动生成 |
| 🤖 AI 超级智能体 | 聊天室风格，分步骤实时输出 | `GET /ai/manus/chat` | ❌ 接口无需 |

## 功能特性

- **主页**：卡片式应用切换，并实时检测后端连通状态（`GET /health`，使用 Axios）。
- **聊天室 UI**：用户消息在右、AI 消息在左，底部输入框；支持打字动画、流式光标、错误气泡、消息复制。
- **实时流式**：通过原生 `EventSource` 消费后端 SSE，逐 token / 逐步骤实时渲染。
- **会话隔离**：恋爱大师进入页面即自动生成 `chatId`（`crypto.randomUUID()`），并可一键「新会话」。
- **中文输入法友好**：正确处理输入法组合状态，避免选词回车误发送（Enter 发送 / Shift+Enter 换行）。

## 快速开始

```bash
# 1. 安装依赖
npm install

# 2. 启动开发服务器（默认 http://localhost:5173）
npm run dev

# 3. 生产构建 / 预览
npm run build
npm run preview
```

> ⚠️ 需要先启动后端服务（`http://localhost:8123`）。后端已配置全局 CORS，前端可直接跨域访问，无需代理。

## 配置后端地址

默认地址前缀为 `http://localhost:8123/api`，在根目录 `.env` 中可修改：

```
VITE_API_BASE_URL=http://localhost:8123/api
```

如遇跨域问题，也可改用 Vite 代理：把 `.env` 中的值改为 `/api`，并在 `vite.config.js` 中打开注释的 `proxy` 配置。

## 目录结构

```
src/
├── api/
│   ├── request.js      # Axios 实例（健康检查、同步接口等非流式请求）
│   └── chat.js         # SSE 地址构造 + 健康检查
├── components/
│   └── ChatRoom.vue    # 可复用的聊天室组件（两个应用共用）
├── config/
│   └── index.js        # 后端地址前缀
├── router/
│   └── index.js        # 路由：/ 主页, /love 恋爱大师, /manus 智能体
├── styles/
│   └── global.css      # 全局样式 / 主题变量
├── utils/
│   ├── id.js           # chatId / 消息 ID 生成
│   └── sse.js          # EventSource 封装（自动处理正常关闭与错误）
├── views/
│   ├── Home.vue        # 主页（应用切换）
│   ├── LoveApp.vue     # 页面 1：AI 恋爱大师
│   └── ManusApp.vue    # 页面 2：AI 超级智能体
├── App.vue
└── main.js
```

## 关于 SSE 的技术说明

两个后端接口均为 **GET 形式的 SSE**，因此使用浏览器原生 `EventSource` 是最简单、最可靠的方案（`utils/sse.js`）。
Axios 用于普通请求（健康检查、同步对话）。由于 `EventSource` 在服务端正常关闭连接时也会触发 `error` 并尝试自动重连，
封装中在收到过数据后主动 `close()`，避免智能体接口被重复触发。
