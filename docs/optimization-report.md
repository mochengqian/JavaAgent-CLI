# JavaAgent CLI 优化报告

> 优化目标：将 JavaAgent CLI 从基础命令行工具升级为 Claude Code 风格的交互式 CLI 应用
>
> 优化轮次：两轮
>
> 测试结果：37 个测试全部通过（原 30 + 新增 7）

---

## 第一轮优化：核心能力补齐

### 1.1 新增文件

| 文件路径 | 说明 |
|---------|------|
| `src/main/java/com/javagent/util/Terminal.java` | ANSI 终端格式化工具类 |
| `src/main/java/com/javagent/tools/EditTool.java` | 精确文本替换编辑工具 |
| `src/main/java/com/javagent/model/ToolDisplayCallback.java` | 工具执行显示回调接口 |
| `src/test/java/com/javagent/tools/EditToolTest.java` | EditTool 单元测试（7 个用例） |

### 1.2 修改文件

| 文件路径 | 说明 |
|---------|------|
| `src/main/java/com/javagent/JavaAgentCLI.java` | CLI 主界面全面升级 |
| `src/main/java/com/javagent/core/Agent.java` | 新增工具执行显示回调 |
| `src/main/java/com/javagent/model/OpenAiCompatibleModelClient.java` | 实现真正 SSE 流式输出 |
| `src/main/java/com/javagent/model/MockModelClient.java` | Mock 模式支持编辑工具 |

---

### 1.3 Terminal.java — ANSI 终端格式化工具类

**功能：** 提供终端颜色和样式支持，自动检测终端能力。

```
提供的方法：
- bold(), dim(), italic()          — 文本样式
- red(), green(), yellow()         — 基础颜色
- cyan(), blue(), magenta()        — 基础颜色
- brightRed(), brightGreen()       — 亮色变体
- brightCyan(), brightYellow()     — 亮色变体
- gray()                           — 灰色
- colorize(ansiCode, text)         — 通用着色
- prompt()                         — 彩色提示符
- isEnabled()                      — 检测颜色支持
```

**自动检测逻辑：**
- 检查 `TERM` 环境变量（`dumb` 则禁用）
- 检查 Windows Terminal / ConEmu / VS Code 终端
- Windows 10+ 的 cmd/powershell 支持 ANSI
- 输出被重定向时自动禁用颜色

---

### 1.4 EditTool.java — 精确文本替换工具

**功能：** 类似 Claude Code 的核心编辑能力，通过 `old_string` 精确匹配文件内容并替换为 `new_string`。

**参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `path` | string | 是 | 文件路径 |
| `old_string` | string | 是 | 要查找的精确文本（含空格缩进） |
| `new_string` | string | 是 | 替换文本 |
| `replace_all` | boolean | 否 | 是否替换所有匹配项，默认 false |

**安全机制：**
- `requiresApproval=true` — 需要用户确认
- `destructive=false` — 不标记为破坏性（受控修改）
- 拒绝编辑二进制文件
- 多个匹配时要求 `replace_all=true` 或更精确的上下文

**智能提示：**
- 找不到精确匹配时，尝试模糊查找首行文本
- 如果发现类似文本，提示"类似文本在第 X 行附近，检查空格/缩进"
- 返回编辑统计：`-N lines, +M lines`

**别名：** `replace`, `str_replace`, `sed`

---

### 1.5 OpenAiCompatibleModelClient.java — SSE 流式输出

**改动前：** 等待 API 返回完整响应 → 切成小块模拟流式输出

**改动后：** 真正的 Server-Sent Events 流式处理

```
请求体新增：
  "stream": true
  "stream_options": { "include_usage": false }

处理流程：
  1. 使用 HttpClient.BodyHandlers.ofLines() 逐行读取 SSE 事件流
  2. 解析每个 "data:" 行的 JSON
  3. 提取 delta.content 并实时调用 streamHandler.onChunk()
  4. 增量解析工具调用的 id/name/arguments（流式工具调用支持）
  5. [DONE] 事件结束流
```

**关键实现细节：**
- `ToolCallDelta` 内部类用于增量累积工具调用信息
- 工具调用的 `arguments` 通过 `StringBuilder` 逐步拼接
- 流式和非流式请求共用 `buildRequestBody()` 方法（`stream` 参数区分）
- 错误响应使用非流式方式处理

---

### 1.6 Agent.java — 工具执行显示回调

**新增接口：** `ToolDisplayCallback`

```java
public interface ToolDisplayCallback {
    void onToolStart(String toolName, String summary);
    void onToolEnd(String toolName, boolean success, String resultSummary);
    default void onToolEnd(String toolName, boolean success, String resultSummary, String fullContent);
}
```

**Agent 改动：**
- `processTurn()` 新增 `ToolDisplayCallback` 参数
- 工具执行前调用 `onToolStart(toolName, summarizeToolCall(toolCall))`
- 工具执行后调用 `onToolEnd(toolName, !result.error(), truncateResult(), fullContent)`
- 系统提示词增加编辑工具使用指导

---

### 1.7 JavaAgentCLI.java — 第一轮改动

| 改动点 | 改动前 | 改动后 |
|--------|--------|--------|
| 提示符 | `javaagent> ` | 青色加粗 `> ` |
| 启动信息 | 五行大学横幅 | 简洁版本 + 模式 + 模型信息 |
| 命令帮助 | 纯文本列表 | 青色命令 + 灰色说明 |
| 工具列表 | 纯文本 | 着色标签 `[auto]` / `[approval]` |
| 审批提示 | `yes/no/cancel` | `? Approval required` 风格 |
| 状态显示 | 纯文本 key=value | 对齐的标签+值格式 |
| 等待指示 | 无 | `Thinking...` 指示器 |
| 工具执行 | 静默 | `read_file pom.xml ... done` 彩色状态行 |

---

### 1.8 MockModelClient.java — 支持 EditTool

- 新增关键词匹配：`edit`、`replace`、`修改`、`替换` → 触发 EditTool
- `summarizeToolResult()` 新增 `edit` 分支的总结模板

---

## 第二轮优化：界面体验打磨

### 2.1 修改文件

| 文件路径 | 说明 |
|---------|------|
| `src/main/java/com/javagent/util/Terminal.java` | 新增 5 个格式化辅助方法 |
| `src/main/java/com/javagent/model/ToolDisplayCallback.java` | 扩展支持完整内容回调 |
| `src/main/java/com/javagent/core/Agent.java` | 传递完整工具结果给回调 |
| `src/main/java/com/javagent/JavaAgentCLI.java` | 全面重写，所有显示优化 |

---

### 2.2 Terminal.java — 新增方法

| 方法 | 说明 | 示例输出 |
|------|------|---------|
| `lineNumber(num, line)` | 带行号的代码行 | `   1 │ <?xml ...` |
| `diffRemove(line)` | Diff 删除行（红色） | `- hello world` |
| `diffAdd(line)` | Diff 新增行（绿色） | `+ goodbye world` |
| `filePath(path)` | 文件路径着色（亮蓝） | `src/Main.java` |
| `truncate(text, maxLen)` | 截断文本 | `hello wor...` |

---

### 2.3 ToolDisplayCallback 接口扩展

新增带完整内容的回调方法：

```java
default void onToolEnd(String toolName, boolean success, String resultSummary, String fullContent) {
    onToolEnd(toolName, success, resultSummary);  // 向后兼容
}
```

Agent 在调用时传递 `result.content()` 作为 `fullContent`，CLI 可根据工具类型做丰富的格式化显示。

---

### 2.4 JavaAgentCLI.java — 第二轮全面重写

#### 2.4.1 启动横幅

**改动前：**
```
  JavaAgent CLI v1.0.0
  A Claude Code-style coding assistant

Mode: mock | Model: gpt-5.4-mini | Type /help for commands
```

**改动后：**
```
  JavaAgent CLI v1.0.0
  A Claude Code-style coding assistant

  cwd:    D:\develop\vs-code\JavaAgent-CLI-mcq
  mode:   mock  model: gpt-5.4-mini
  tools:  6 registered
  tip:    type /help for commands

  Session restored: Session 2026-04-29 11:17 (64 messages)
```

新增工作目录、工具数量、会话恢复详情。

#### 2.4.2 工具执行结果紧凑显示

每种工具有专属的格式化显示：

**read_file：**
```
  read_file pom.xml ... done
    Lines: 1-83 of 83
    1 │ <?xml version="1.0" encoding="UTF-8"?>
    2 │ <project xmlns="http://maven.apache.org/POM/4.0.0"
    3 │          xmlns:xsi="..."
    4 │          xsi:schemaLocation="...">
    5 │     <modelVersion>4.0.0</modelVersion>
    ... (78 more lines)
```

**grep：**
```
  grep "Agent" . ... done
    src\main\java\com\javagent\core\Agent.java:1:class Agent {
    src\main\java\com\javagent\core\Agent.java:22:public class Agent {
    ... (more matches)
    files_scanned=31, matches=45
```

**edit：**
```
  edit test.txt ... done
    Edited D:\...\test.txt (-1 lines, +1 lines)
```

**list_directory：**
```
  list_directory . ... done
    src/                    ← 目录蓝色高亮
    pom.xml
    README.md
    12 entries
```

**bash：**
```
  bash mvn test ... done
    $ mvn test              ← 命令青色高亮
    [INFO] BUILD SUCCESS
    [exit=0]
```

#### 2.4.3 审批界面

**改动前：**
```
Approval required:
  tool: edit
  args: path=test.txt, old_string=hello, new_string=world
Approve? [yes/no/cancel]:
```

**改动后：**
```
  ? Approval required
    tool edit
    path: test.txt
    old_string: old "hello"
    new_string: new "world"
    preview:
    - hello                   ← 红色删除行
    + world                   ← 绿色新增行
    Approve? [y]es / [n]o / [c]ancel:
```

改动点：
- 参数按类型分类着色（路径蓝色、内容灰色、old 红色、new 绿色）
- edit 工具自动显示 diff 预览（红删绿增，最多 6 行）
- 长内容自动截断，多行内容显示行数

#### 2.4.4 会话列表

**改动前：**
```
- ef7870c2 | Session 2026-04-29 11:17 | 05-10 23:14 | messages=64
```

**改动后：**
```
  Sessions:
  ───────────────────────────────────────────────────
 * ef7870c2  Session 2026-04-29 11:17  05-10 23:14  68 msgs
  ───────────────────────────────────────────────────
  * = current session
```

改动点：
- 当前会话用 `*` 标记（亮绿色）
- 当前会话标题加粗
- 会话 ID 着色（当前会话亮青色）
- 分隔线美化

#### 2.4.5 Status 显示

分三组显示，每组有分隔线：

```
  Status:
  ───────────────────────────────────────────────────
    mode            mock
    model           gpt-5.4-mini
    streaming       on
    bash            disabled
    approval cache  0 entries
  ───────────────────────────────────────────────────
    session         Session 2026-04-29 11:17
    session id      ef7870c2
    messages        64
  ───────────────────────────────────────────────────
    tools           6 registered
    config          D:\...\config.properties
    sessions dir    D:\...\.javaagent-cli\sessions
```

#### 2.4.6 命令建议

输入错误命令时，使用 Levenshtein 编辑距离算法推荐最接近的命令：

```
> /hepl
  Unknown command: /hepl
  Did you mean: /help ?

> /tool
  Unknown command: /tool
  Did you mean: /tools ?
```

- 阈值：编辑距离 ≤ 3
- 遍历所有已知命令（15 个），找到距离最小的

#### 2.4.7 文本换行

新增 `printWrapped()` 方法，长文本按单词边界自动换行（100 字符宽度）。

---

## 测试结果

### 原有测试（30 个）

| 测试类 | 用例数 | 状态 |
|--------|--------|------|
| AgentTest | 8 | 全部通过 |
| ConfigTest | 1 | 通过 |
| ConversationManagerTest | 2 | 全部通过 |
| MockModelClientTest | 5 | 全部通过 |
| DeleteFileToolTest | 2 | 全部通过 |
| GrepToolTest | 4 | 全部通过 |
| ListDirectoryToolTest | 2 | 全部通过 |
| ReadFileToolTest | 4 | 全部通过 |
| WriteFileToolTest | 2 | 全部通过 |

### 新增测试（7 个）

| 测试类 | 用例 | 状态 |
|--------|------|------|
| EditToolTest | 正常替换 | 通过 |
| EditToolTest | 找不到文本报错 | 通过 |
| EditToolTest | 多个匹配报错 | 通过 |
| EditToolTest | replace_all 批量替换 | 通过 |
| EditToolTest | 文件不存在报错 | 通过 |
| EditToolTest | 空 old_string 报错 | 通过 |
| EditToolTest | 新旧相同报错 | 通过 |

```
Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 文件变更清单

### 新增文件（4 个）

```
src/main/java/com/javagent/util/Terminal.java
src/main/java/com/javagent/tools/EditTool.java
src/main/java/com/javagent/model/ToolDisplayCallback.java
src/test/java/com/javagent/tools/EditToolTest.java
```

### 修改文件（4 个）

```
src/main/java/com/javagent/JavaAgentCLI.java          — 全面重写
src/main/java/com/javagent/core/Agent.java             — 新增回调 + 系统提示词改进
src/main/java/com/javagent/model/OpenAiCompatibleModelClient.java — SSE 流式输出
src/main/java/com/javagent/model/MockModelClient.java  — 支持 edit 关键词
```

### 未变更文件（21 个）

```
pom.xml
config.properties
src/main/java/com/javagent/core/ApprovalDecision.java
src/main/java/com/javagent/core/ApprovalHandler.java
src/main/java/com/javagent/core/ApprovalManager.java
src/main/java/com/javagent/core/ApprovalOutcome.java
src/main/java/com/javagent/core/Config.java
src/main/java/com/javagent/core/ConversationManager.java
src/main/java/com/javagent/model/Message.java
src/main/java/com/javagent/model/ModelClient.java
src/main/java/com/javagent/model/ModelResponse.java
src/main/java/com/javagent/model/Role.java
src/main/java/com/javagent/model/TextStreamHandler.java
src/main/java/com/javagent/model/ToolCall.java
src/main/java/com/javagent/model/ToolResultMessage.java
src/main/java/com/javagent/tools/BashTool.java
src/main/java/com/javagent/tools/DeleteFileTool.java
src/main/java/com/javagent/tools/FileToolSupport.java
src/main/java/com/javagent/tools/GrepTool.java
src/main/java/com/javagent/tools/ListDirectoryTool.java
src/main/java/com/javagent/tools/ReadFileTool.java
src/main/java/com/javagent/tools/Tool.java
src/main/java/com/javagent/tools/ToolDefinition.java
src/main/java/com/javagent/tools/ToolExecutionResult.java
src/main/java/com/javagent/tools/ToolRegistry.java
src/main/java/com/javagent/tools/WriteFileTool.java
```

---

## 优化前后对比总览

| 特性 | 优化前 | 优化后 |
|------|--------|--------|
| 终端颜色 | 无 | ANSI 全彩，自动检测终端支持 |
| 提示符 | `javaagent> ` | 青色加粗 `> ` |
| 流式输出 | 假流式（切块模拟） | 真 SSE 流式（逐 token 输出） |
| 文件编辑 | 只有全文写入 | 精确文本替换（EditTool） |
| 工具执行显示 | 静默执行 | 彩色 `tool_name args ... done` |
| 工具结果 | 原始文本 dump | 紧凑格式（行号、着色、截断） |
| 审批界面 | 纯文本 | 着色参数 + diff 预览 |
| 启动横幅 | 大学信息块 | 简洁版本 + 运行时信息 |
| 会话列表 | 纯文本 | 当前会话标记 + 着色 |
| Status | 纯文本 | 分组对齐显示 |
| 错误命令 | `Unknown command` | 自动推荐最接近的命令 |
| 系统提示词 | 基础描述 | 包含编辑工具使用规范 |
| 工具数量 | 6 个 | 7 个（+edit） |
| 测试用例 | 30 个 | 37 个（+7） |
