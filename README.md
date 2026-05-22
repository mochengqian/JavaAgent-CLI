# JavaAgent CLI

JavaAgent CLI 是一个 Claude Code 风格的 Java 命令行 Agent，具备完整的工具调用、文件编辑、流式输出和交互式终端能力。

## 核心能力

- **Agent 循环** — ReAct 风格：用户输入 → 模型思考 → 调用工具 → 结果回灌 → 继续回答
- **文件编辑** — 精确字符串替换，带彩色 diff 预览
- **SSE 流式输出** — token-by-token 实时显示模型回复
- **Thinking 模型** — 支持 reasoning_content 推理链
- **Claude Code 风格 UI** — 方框边框、彩色输出、Markdown 渲染、Braille 动画
- **安全机制** — 工作区隔离、审批确认、危险命令检测、敏感信息脱敏
- **跨平台** — Windows / macOS / Linux

## Agent 循环流程图

```text
┌─────────────────────────────────────────────────────────────────┐
│                        用户输入文本                               │
└──────────────────────────┬──────────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                  添加到对话历史 (ConversationManager)              │
│                  上下文压缩 (compactIfNeeded)                     │
└──────────────────────────┬──────────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│              调用模型 (ModelClient.chat)                          │
│         ┌──────────────────────────────────────┐                │
│         │  MockModelClient  │ OpenAI 客户端     │                │
│         │  (关键词匹配)      │ (SSE 流式)        │                │
│         └──────────────────────────────────────┘                │
└──────────────────────────┬──────────────────────────────────────┘
                           ▼
              ┌────────────┴────────────┐
              │     模型返回类型？        │
              └────────────┬────────────┘
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
   ┌──────────┐     ┌───────────┐     ┌──────────┐
   │  TEXT    │     │ TOOL_CALLS│     │  ERROR   │
   │ 纯文本   │     │ 请求工具   │     │ 请求失败  │
   └────┬─────┘     └─────┬─────┘     └────┬─────┘
        │                 │                │
        ▼                 ▼                ▼
  ┌──────────┐    ┌──────────────┐   ┌──────────┐
  │ 返回结果  │    │ 审批检查      │   │ 返回错误  │
  │ 给用户    │    │ (ApprovalMgr)│   │ 给用户    │
  └──────────┘    └──────┬───────┘   └──────────┘
                         ▼
              ┌──────────┴──────────┐
              │  是否需要审批？       │
              └──────────┬──────────┘
           ┌─────────────┼─────────────┐
           ▼             ▼             ▼
     ┌──────────┐  ┌──────────┐  ┌──────────┐
     │ 自动通过  │  │ 问用户    │  │ 自动拒绝  │
     │ 只读工具  │  │ yes/no   │  │ 外部路径  │
     └────┬─────┘  └────┬─────┘  └────┬─────┘
          │             │             │
          ▼             ▼             ▼
     ┌──────────────────────────────────────┐
     │          执行工具 (Tool.execute)       │
     │   记录统计 (ToolStats)                │
     │   脱敏结果 (Sanitizer)                │
     └──────────────────┬───────────────────┘
                        ▼
              ┌─────────┴─────────┐
              │  执行成功？         │
              └─────────┬─────────┘
           ┌────────────┴────────────┐
           ▼                         ▼
     ┌──────────┐             ┌──────────┐
     │ 连续失败  │             │ 结果回灌  │
     │ ≥3 次中断 │             │ 继续循环  │
     └──────────┘             └──────────┘
```

## 环境要求

- Java 21+
- Maven 3.8+

```bash
java -version
mvn -v
```

## 快速开始

```bash
cd /path/to/javaagent-cli
mvn clean package -DskipTests
java -jar target/javaagent-cli-1.0.0.jar --mock
```

启动后看到

```bash
╭─ JavaAgent CLI v1.0.0 ─────────────────────────────────────────────────────╮
│                                       │  快速开始                           │
│                                       │ ───────────────────────────────────│
│  输入问题，Agent 自动调用工具帮你       │  /help  查看所有命令                 │
│  只读工具(read,grep,ls)免审批运行      │  /tools  查看可用工具                │
│  文件编辑(edit,write,delete)需确认     │  /status  查看运行状态              │
│  输入 / 然后按 Tab 键自动补全命令       │  /exit  退出程序                    │
│                                       │ ───────────────────────────────────│
│  D:\develop\vs-code\JavaAgent-CLI-mcq │  real · mimo-v2.5-pro · 8 tools    │
╰────────────────────────────────────────────────────────────────────────────╯
  会话: D:developvs-codeJavaAgent-CLI-mcqsrcm... (103 条消息)

──────────────────────────────────────────────────────────────────────────────
>
```

即进入交互模式。

## 工具列表

| 工具             | 别名                 | 说明                           | 审批     |
| ---------------- | -------------------- | ------------------------------ | -------- |
| `read_file`      | `cat`                | 读取文本文件                   | 自动通过 |
| `write_file`     | `write`, `save_file` | 写入文件（带 60 行预览）       | 需要审批 |
| `edit`           | `replace`, `sed`     | 精确字符串替换（带 diff 预览） | 需要审批 |
| `delete_file`    | `rm`                 | 删除文件                       | 需要审批 |
| `grep`           | `search`, `find`     | 正则搜索文本                   | 自动通过 |
| `list_directory` | `ls`, `dir`          | 列出目录内容                   | 自动通过 |
| `bash`           | `shell`, `exec`      | 执行 shell 命令                | 需要审批 |
| `network`        | `http`, `fetch`      | HTTP 请求                      | 需要审批 |

`bash` 工具默认关闭，执行 `/bash on` 后启用。

## 斜杠命令

| 命令                        | 说明                    |
| --------------------------- | ----------------------- |
| `/help`                     | 显示可用命令            |
| `/exit`, `/quit`            | 退出程序                |
| `/clear`, `/new [title]`    | 新建会话                |
| `/save [title]`             | 保存当前会话            |
| `/load [id\|title\|latest]` | 加载已保存的会话        |
| `/sessions`                 | 列出已保存的会话        |
| `/tools`                    | 列出已注册的工具        |
| `/mode mock\|real`          | 切换模型模式            |
| `/stream on\|off`           | 开关流式输出            |
| `/bash on\|off`             | 开关 Bash 工具          |
| `/bypass on\|off`           | 跳过所有工具审批确认    |
| `/prompt show\|set\|reset`  | 管理自定义系统提示词    |
| `/approvals clear`          | 清空审批缓存            |
| `/reload`                   | 重新加载配置文件        |
| `/export`                   | 导出当前对话为 Markdown |
| `/stats`                    | 查看工具执行统计        |
| `/status`                   | 显示运行状态            |
| `/network`                  | 网络请求工具            |

输入 `/` 然后 双击 Tab 可自动补全命令。上下方向键可切换补全全。按第一次enter可以补全命令。按第二次enter可以执行命令。可以根据自身需要可以开启bypass模式，跳过所有工具审批确认但是后果需自行承担。

## 配置

在项目根目录创建 `config.properties`（图示为默认值）：

```properties
# 模式
agent.mock_mode=true           # 是否使用模拟模式（不实际调用API）
agent.api_key=                 # API 密钥
agent.base_url=https://api.openai.com/v1  # API 基础 URL
agent.model=gpt-5.4-mini       # 使用的模型名称

# 行为
agent.auto_save=true           # 是否自动保存会话
agent.max_iterations=12        # 最大工具调用循环次数
agent.enable_bash=false        # 是否启用 Bash 工具
agent.stream_responses=true    # 是否流式输出响应
agent.approval_cache=true      # 是否启用审批缓存
agent.allow_external_paths=false  # 是否允许访问工作区外路径
agent.bypass_permissions=false # 是否跳过所有工具审批确认

# 高级
agent.system_prompt=           # 自定义系统提示词
agent.max_context_messages=100 # 最大上下文消息数量
agent.rate_limit_qps=10        # API 请求速率限制（每秒请求数）
```

配置文件查找顺序：

1. 项目根目录（有 `pom.xml` 的目录）
2. 工作目录
3. `~/.javaagent-cli/config.properties`

运行时可通过 `/reload` 重载配置。

## 安全机制

- **工作区隔离** — 所有文件操作限制在项目目录内
- **审批确认** — 写文件、删文件、bash、网络请求需要用户确认
- **危险命令检测** — 10 类正则匹配（rm -rf、fork bomb、reverse shell 等）
- **敏感信息脱敏** — API Key/Token 自动过滤，防止泄漏到会话文件
- **连续失败保护** — 同一工具连续失败 3 次自动中断

## 项目结构

```text
src/main/java/com/javagent/
├── JavaAgentCLI.java          # CLI 入口，终端交互，命令处理
├── BannerPrinter.java         # 启动横幅
├── SlashCommandCompleter.java # 命令自动补全
├── core/
│   ├── Agent.java             # Agent 核心循环
│   ├── Config.java            # 配置管理
│   ├── ConversationManager.java # 会话管理
│   ├── ApprovalManager.java   # 审批管理
│   └── ToolStats.java         # 工具执行统计
├── model/
│   ├── Message.java           # 消息结构
│   ├── ModelClient.java       # 模型客户端接口
│   ├── OpenAiCompatibleModelClient.java # OpenAI API 客户端（SSE 流式）
│   ├── MockModelClient.java   # Mock 客户端
│   └── ToolDisplayCallback.java # 工具执行回调
├── tools/
│   ├── Tool.java              # 工具接口
│   ├── ToolRegistry.java      # 工具注册中心
│   ├── ToolProvider.java      # SPI 插件接口
│   ├── FileToolSupport.java   # 文件工具辅助类
│   ├── ReadFileTool.java      # 读文件
│   ├── WriteFileTool.java     # 写文件（带预览）
│   ├── EditTool.java          # 编辑工具（带 diff）
│   ├── DeleteFileTool.java    # 删文件
│   ├── GrepTool.java          # 搜索工具
│   ├── ListDirectoryTool.java # 列目录
│   ├── BashTool.java          # Bash 工具
│   └── NetworkTool.java       # HTTP 请求工具
└── util/
    ├── Terminal.java          # ANSI 终端颜色
    ├── MarkdownRenderer.java  # Markdown 渲染
    ├── Sanitizer.java         # 敏感信息过滤
    └── RateLimiter.java       # 速率限制
```

## 测试

```bash
mvn test
```

66 个测试，覆盖核心逻辑、工具执行、审批管理。

## 常见问题

### 找不到 POM

```bash
cd /path/to/javaagent-cli
mvn clean package -DskipTests
```

### 找不到 Java (macOS)

```bash
/opt/homebrew/opt/openjdk/bin/java -jar target/javaagent-cli-1.0.0.jar --mock
```

### 启动后恢复旧会话

输入 `/clear` 开始新会话。

### real 模式报 API key 错误

确保 `config.properties` 中配置了 `agent.api_key`，或切回 `/mode mock`。
