/**
 * 全局配置
 */

// 后端接口地址前缀（端口 8123 + context-path /api）
// 优先读取环境变量 VITE_API_BASE_URL，未配置时使用默认值
export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8123/api'
