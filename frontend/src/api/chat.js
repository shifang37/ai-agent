import { API_BASE_URL } from '../config'
import request from './request'

/**
 * 构造「AI 恋爱大师」SSE 请求地址
 * 对应后端：GET /ai/love_app/chat/sse?message=&chatId=
 */
export function buildLoveAppSseUrl(message, chatId) {
  const params = new URLSearchParams({ message, chatId })
  return `${API_BASE_URL}/ai/love_app/chat/sse?${params.toString()}`
}

/**
 * 构造「AI 超级智能体」SSE 请求地址
 * 对应后端：GET /ai/manus/chat?message=
 */
export function buildManusSseUrl(message) {
  const params = new URLSearchParams({ message })
  return `${API_BASE_URL}/ai/manus/chat?${params.toString()}`
}

/** 健康检查：GET /health -> "ok"（用于主页展示后端连通状态，使用 axios） */
export function healthCheck() {
  return request.get('/health')
}

/** 恋爱大师同步对话（非流式）：GET /ai/love_app/chat/sync（备用，使用 axios） */
export function chatWithLoveAppSync(message, chatId) {
  return request.get('/ai/love_app/chat/sync', { params: { message, chatId } })
}

/**
 * 确认/拒绝超级智能体的高危工具调用（Human-in-the-loop）
 * 对应后端：POST /ai/manus/confirm?agentId=&approved=
 */
export function confirmManusToolCall(agentId, approved) {
  return request.post('/ai/manus/confirm', null, { params: { agentId, approved } })
}
