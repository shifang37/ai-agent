/**
 * 生成唯一的会话 ID（chatId），用于区分不同聊天室会话。
 * 优先使用浏览器原生 crypto.randomUUID()，不支持时回退到时间戳 + 随机数。
 */
export function generateChatId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `chat-${Date.now()}-${Math.floor(Math.random() * 1e6)}`
}

/** 生成一次性的消息 ID（用于 v-for key） */
let seed = 0
export function uid() {
  seed += 1
  return `${Date.now().toString(36)}-${seed}`
}
