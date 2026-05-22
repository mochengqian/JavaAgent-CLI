# 更新日志

基于初始版本 (`a1d0600`) 的所有变更记录。

---

## 新增功能

### 编辑工具 (EditTool)
- 新增 `edit` 工具，支持精确字符串替换（Claude Code 核心能力）
- 支持 `replace_all` 批量替换
- 编辑成功后显示彩色 diff 预览（深红删除 / 深绿添加）
- 精确匹配失败时展示文件实际内容（带行号和 `>>>` 标记），辅助模型自我修正

### 网络工具 (NetworkTool)
- 新增 `network` 工具，支持 HTTP GET/POST/PUT/DELETE 请求
- 别名：`http`、`fetch`、`request`

### JLine3 终端升级
- 用 JLine3 `LineReader` 替代 `BufferedReader`
- 支持行编辑、方向键历史翻页、持久化输入历史
- 斜杠命令 Tab 自动补全

### Claude Code 风格 UI
- 方框边框工具执行显示 `┌ ┐ └ ┘ │`
- Braille 动画 spinner 显示"思考中..."
- ANSI 彩色输出（通过 `Terminal` 工具类）
- Markdown-to-ANSI 渲染器，支持标题、代码块、加粗、列表
- 方框风格启动横幅 (BannerPrinter)
- 动态宽度分隔线

### 新增斜杠命令
- `/bypass on|off` — 跳过所有工具审批确认
- `/reload` — 运行时重载配置文件
- `/export` — 导出当前对话为 Markdown
- `/stats` — 查看工具执行统计
- `/network` — 网络请求工具

### SSE 流式输出
- 实现真正的 Server-Sent Events streaming
- 支持 token-by-token 实时输出
- 支持流式拼接 tool_call 的 id/name/arguments

### Thinking 模型支持
- `reasoningContent` 字段贯穿 Message → ModelResponse → Agent 全链路
- 自动解析 thinking 模型的推理内容
- API 因 reasoning_content 缺失报错时，自动清理旧上下文并重试

### 安全增强
- **敏感信息脱敏**：`Sanitizer` 过滤 API Key/Token，防止泄漏到会话 JSON
- **工作区安全检查**：所有文件操作限制在工作区内 (`FileToolSupport.checkInsideWorkspace`)
- **危险命令检测增强**：从 5 条字符串匹配扩展为 10 类正则匹配（rm -rf、fork bomb、reverse shell 等）
- **跨平台 Bash**：Windows 用 `cmd.exe /c`，Unix 用 `/bin/bash -lc`

### 可靠性增强
- **连续失败保护**：同一工具连续失败 3 次自动中断 Agent 循环
- **API 自动重试**：最多 3 次重试，指数退避
- **工具降级**：HTTP 400 时自动去除 tools 参数重试
- **速率限制**：令牌桶算法控制 API 请求频率
- **上下文压缩**：`compactIfNeeded` 防止上下文超限
- **配置校验**：启动时检查参数合法性并打印警告
- **默认循环次数调整**：`agent.max_iterations` 默认值从 6 改为 12，支持更复杂的多步骤任务

### 会话管理增强
- 首条用户消息自动设置会话标题
- 对话导出为 Markdown (`/export`)
- Session JSON 向后兼容 (`@JsonIgnoreProperties`)
- JVM Shutdown Hook 保存会话

### 插件系统
- `ToolProvider` SPI 接口，通过 `ServiceLoader` 自动发现外部工具插件

### 写入预览
- `write_file` 创建文件后显示前 60 行带行号预览

### 工具执行统计
- `ToolStats` 追踪每个工具的调用次数、总耗时、错误次数、平均耗时

---

## Bug 修复

1. **二进制文件误判** — 原版 UTF-8 严格解码导致中文/emoji 被判为二进制。修复为只检测 null 字节
2. **Session 加载兼容** — 新增字段后旧 JSON 反序列化失败。添加 `@JsonIgnoreProperties(ignoreUnknown=true)`
3. **Agent 死循环** — 工具反复失败时无限循环。添加连续失败检测（3 次中断）
4. **Role 序列化不一致** — 枚举值大写序列化但 API 期望小写。添加 `@JsonValue`/`@JsonCreator`
5. **Mock 不支持 edit** — MockModelClient 缺少 edit 关键词匹配。已补全
6. **Mock Windows 路径** — FILE_PATTERN 正则不匹配 Windows 路径。已扩展
7. **Auto-save 异常吞掉** — 原版 `catch (IOException ignored)`。改为写日志
8. **API Reasoning Content 缺失** — 旧上下文缺少 reasoning_content 导致 400。自动清理重试

---

## 依赖更新

- 新增 JLine3 3.28.0（terminal、reader、builtins）

---

## 测试

- 新增 `EditToolTest`、`BashToolTest`、`ApprovalManagerTest`、`IntegrationTest`
- 总测试数从 ~30 增加到 66
