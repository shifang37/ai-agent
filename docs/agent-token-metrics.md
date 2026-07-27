# ai-agent Agent 任务 Token 用量与调用链路实测报告

项目：AI 智能体应用平台（项目文件夹：`ai-agent`）
日期：2026-07-13
测量方式：`cd backend && ./mvnw test -Dtest=AgentTokenMetricsTest`
（直接构造 SuperAgent 智能体执行 3 个真实任务，数据来自 `AgentMetricsCollector`，
与线上 `GET /api/monitor/metrics` 接口同一数据源；模型 qwen-plus）

## 实测数据（修复 terminate 缺陷后）

| 任务类型 | 总 token（输入/输出） | LLM 调用 | LLM 单次平均耗时 | 工具调用 | 任务总耗时 |
|---|---|---|---|---|---|
| 直答类：3 天杭州行程建议 | 1,494（1,094 / 400） | 1 次 | 8,674 ms | doTerminate 2ms | 8.7 s |
| 搜索+总结：2026 世界杯举办地 | 4,046（3,969 / 77） | 2 次 | 1,122 ms | searchWeb 3,652ms | 5.9 s |
| 网页抓取+概括：codefather.cn | 539,213（538,921 / 292） | 2 次 | 29,138 ms | scrapeWebPage 399ms | 58.7 s |

**典型任务（直答/搜索类）平均消耗约 1.5k-4k token，1-2 次 LLM 调用，6-9 秒完成。**

## 监控数据驱动的两个真实发现与修复

### 1. terminate 工具不终止执行循环（严重缺陷，已修复）

监控显示「3 天杭州行程」任务竟产生 **20 次 LLM 调用、20 次 doTerminate 工具调用**：
智能体第 1 步就完成任务并调用 terminate，但 `ToolCallAgent.act()` 缺少终止检测，
执行循环空转到 maxSteps=20 才结束，每一步还携带全量历史上下文重复请求模型。

修复（`ToolCallAgent.act()` 检测 doTerminate 响应后置 `AgentState.FINISHED`）前后对比：

| 任务 | token 消耗 | LLM 调用次数 | 任务耗时 |
|---|---|---|---|
| 3 天杭州行程 | 65,265 → 1,494（**-97.7%**） | 20 → 1 | 48.9s → 8.7s |
| 世界杯搜索 | 45,169 → 4,046（**-91.0%**） | 11 → 2 | 33.0s → 5.9s |

### 2. 网页抓取工具全量 HTML 入上下文（已识别，待优化）

`WebScrapingTool` 将整页原始 HTML 直接返回给模型，抓取 codefather.cn 首页导致单次
LLM 调用输入 53.9 万 token（依赖 qwen-plus 长上下文才没有报错），单任务耗时近 1 分钟。
后续优化方向：抓取结果做正文抽取（如 Jsoup text()）+ 长度截断，预计可将该类任务
token 消耗降低 95% 以上。

### 3. 附带修复：DashScope Usage 的 completionTokens 恒为 0

spring-ai-alibaba M6.1 的 Usage 实现不填 completion 字段，`AgentMetricsCollector`
已按 `total - prompt` 推导补齐，输入/输出分项统计恢复正常。

## 简历表述参考

> 【ai-agent】自研 Token 用量与调用链路监控（自定义 Advisor 提取 ChatResponse 的
> TokenUsage，按会话累计输入/输出 token、LLM/工具调用次数与时延，暴露 /monitor/metrics
> 统计接口）；实测典型 Agent 任务平均消耗 1.5k-4k token、1-2 次 LLM 调用；并依据监控
> 数据定位修复 terminate 工具不终止执行循环的缺陷，单任务 token 消耗降低 91%-98%
> （65k→1.5k）、耗时降低 82%，同时识别出网页抓取全量 HTML 入上下文的成本热点。
