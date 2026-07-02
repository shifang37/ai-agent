/**
 * SSE（Server-Sent Events）连接封装。
 *
 * 后端两个流式接口都是 GET 请求，因此使用浏览器原生 EventSource 最为可靠：
 *   - /ai/love_app/chat/sse  返回 Flux<String>（逐 token 推送）
 *   - /ai/manus/chat         返回 SseEmitter（逐步骤推送 "Step N: ..."）
 *
 * 注意：EventSource 在服务端“正常关闭连接”时也会触发 onerror 并尝试自动重连，
 * 因此这里在 onerror 中主动 close()，避免智能体接口被重复触发。
 */
export function createSSEConnection(url, { onMessage, onDone, onError } = {}) {
  const source = new EventSource(url)
  let received = false
  let finished = false

  const finish = (isError, err) => {
    if (finished) return
    finished = true
    source.close()
    if (isError) {
      onError && onError(err)
    } else {
      onDone && onDone()
    }
  }

  source.onmessage = (event) => {
    received = true
    onMessage && onMessage(event.data)
  }

  source.onerror = () => {
    // 已经收到过数据 => 视为服务端正常结束；否则视为连接失败
    if (received) {
      finish(false)
    } else {
      finish(true, new Error('无法连接到服务器，请确认后端服务 (http://localhost:8123) 已启动'))
    }
  }

  // 返回一个控制器，便于外部手动中断（例如用户点击“停止”）
  return {
    close: () => finish(false)
  }
}
