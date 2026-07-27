# AI Agent — AI 恋爱大师 & AI 超级智能体

基于 **Spring AI Alibaba + Vue 3** 的 AI 应用集合，包含两个核心应用：

- **AI 恋爱大师（LoveApp）**：基于 RAG（检索增强生成）的恋爱咨询对话应用，支持多轮对话记忆、本地/云端知识库检索。
- **AI 超级智能体（SuperAgent）**：基于 ReAct 模式的自主规划智能体，可自动调用工具（联网搜索、网页抓取、文件读写、PDF 生成、资源下载、终端命令等）逐步完成复杂任务。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 3.5、Spring AI / Spring AI Alibaba（灵积 DashScope，qwen-plus） |
| RAG | SimpleVectorStore（本地缓存）、PgVector（PostgreSQL 向量库）、DashScope 云端知识库 |
| 工具调用 | Tool Calling + MCP（Model Context Protocol） |
| MCP 服务 | `search-mcp` 子模块：基于 Pexels API 的图片搜索 MCP Server（支持 stdio / SSE） |
| 前端 | Vue 3、Vite、Vue Router、Axios（SSE 流式输出） |
| 接口文档 | SpringDoc + Knife4j（`/api/swagger-ui.html`） |

## 项目结构

```
ai-agent
├── backend/                        # Spring Boot 后端
│   ├── src/main/java/com/tzy/backend/
│   │   ├── agent/                  # ReAct 智能体（BaseAgent / ReActAgent / ToolCallAgent / SuperAgent）
│   │   ├── app/                    # AI 恋爱大师应用（LoveApp）
│   │   ├── advisor/                # 自定义 Advisor（日志、重读等）
│   │   ├── rag/                    # RAG：文档加载、向量库配置、查询增强
│   │   ├── tools/                  # 智能体工具（搜索/抓取/文件/PDF/终端等）
│   │   ├── controller/             # REST + SSE 接口
│   │   └── demo/                   # 多种 AI 接入方式示例
│   └── search-mcp/                 # 图片搜索 MCP Server 子模块
└── frontend/                       # Vue 3 前端（首页 / 恋爱大师 / 超级智能体）
```

## 快速开始

### 1. 后端配置

后端使用 `local` profile，敏感配置**不入库**。在 `backend/src/main/resources/` 下新建 `application-local.yml`（已被 `.gitignore` 忽略）：

```yaml
spring:
  ai:
    dashscope:
      api-key: <你的 DashScope API Key>
      chat:
        options:
          model: qwen-plus
    mcp:
      client:
        stdio:
          servers-configuration: classpath:mcp-servers.json
    vectorstore:
      pgvector:
        index-type: HNSW
        dimensions: 1536
        distance-type: COSINE_DISTANCE
  datasource:  # 可选：使用 PgVector 时配置 PostgreSQL
    url: jdbc:postgresql://<host>:5432/<db>
    username: <username>
    password: <password>

search-api:
  api-key: <你的 SearchAPI Key>   # https://www.searchapi.io
```

`search-mcp` 模块的 Pexels 图片搜索需要配置环境变量 `PEXELS_API_KEY`（从 https://www.pexels.com/api/ 申请）。

### 2. 启动后端

```bash
cd backend
# 先构建 MCP Server（主应用通过 stdio 启动它）
mvn clean package -DskipTests -f search-mcp/pom.xml
mvn spring-boot:run
```

后端运行在 `http://localhost:8123/api`，接口文档见 `http://localhost:8123/api/swagger-ui.html`。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`（后端地址可通过环境变量 `VITE_API_BASE_URL` 覆盖）。

## 核心接口

| 接口 | 说明 |
| --- | --- |
| `GET /api/ai/love_app/chat/sync` | 恋爱大师同步对话（`message`、`chatId`） |
| `GET /api/ai/love_app/chat/sse` | 恋爱大师 SSE 流式对话 |
| `GET /api/ai/manus/chat` | 超级智能体 SSE 流式对话，实时输出每步思考与工具调用 |
| `GET /api/health` | 健康检查 |

## 主要特性

- **多轮对话记忆**：基于 `chatId` 隔离会话，支持自定义 ChatMemory。
- **RAG 三种方案**：本地 SimpleVectorStore（带落盘缓存，避免重复向量化）、PgVector 向量数据库（启动时检测已有数据跳过重复加载）、DashScope 云端知识库。
- **ReAct 智能体**：思考（Reasoning）→ 行动（Acting）循环，最大步数控制，SSE 实时推送执行过程。
- **MCP 集成**：既作为 MCP 客户端调用图片搜索服务，也提供独立的 MCP Server 模块。
