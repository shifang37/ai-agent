import axios from 'axios'
import { API_BASE_URL } from '../config'

/**
 * Axios 实例：用于普通（非流式）请求，例如健康检查、同步对话等。
 * 流式（SSE）接口请使用 utils/sse.js 中的 EventSource 封装。
 */
const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

// 响应拦截：直接返回 data，统一错误处理
request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    console.error('[request error]', error)
    return Promise.reject(error)
  }
)

export default request
