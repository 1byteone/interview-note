# 🎮 sql-mother + sql-generator + yuindex + yu-picture 前端项目剖析

> 四个高 Star 前端/全栈项目的技术拆解

---

# Part 1: sql-mother — SQL 之母 (4,326⭐)

## 纯前端 SQL 执行引擎的精妙设计

### 核心原理: sql.js WebAssembly

**没有后端数据库**，SQL 完全在浏览器内执行：

```
浏览器内:
Monaco Editor (用户输入SQL)
       ↓
sqlExecutor.ts (~30行代码)
       ↓
sql.js (Emscripten 编译的 SQLite C→Wasm)
       ↓
sql-wasm.wasm (SQLite 引擎在浏览器中运行)
```

**关键代码**:
```typescript
// sqlExecutor.ts
import initSqlJs from "sql.js";

let SQL: SqlJsStatic; // 单例

export const initDB = async (initSql?: string) => {
  SQL = await initSqlJs({ locateFile: () => "./sql-wasm.wasm" });
  const db = new SQL.Database(); // 内存数据库
  if (initSql) db.run(initSql);  // 建表+插入数据
  return db;
};

export const runSQL = (db: Database, sql: string) => {
  return db.exec(sql); // 返回 QueryExecResult[]
};
```

### 判题机制 — 不比 SQL 语句，比执行结果

```typescript
// 分别执行用户SQL和答案SQL，然后对比结果
const userResult = runSQL(db, userInput);
const answerResult = runSQL(db, level.answer);

// 对比: 列名必须一致 + 数据行必须一致
if (JSON.stringify(userResult.columns) !== JSON.stringify(answerResult.columns))
  return ERROR;
if (JSON.stringify(userResult.values) === JSON.stringify(answerResult.values))
  return SUCCEED;
```

**设计亮点**:
- 允许不同 SQL 写法得到相同正确答案
- JSON 序列化对比避免复杂嵌套循环
- 每次切关卡重建内存数据库，零状态残留

### 关卡系统 — 每关一个目录

```
level1/
├── index.ts          ← 关卡定义 (key, title, answer, hint)
├── createTable.sql   ← 建表+测试数据
└── README.md         ← 教程文档 (Markdown)
```

30 个主线关卡 + 16 个社区贡献关卡 = 46+ 关卡

---

# Part 2: sql-generator — SQL 生成器 (3,427⭐)

## JSON 模板 DSL → SQL 的递归展开引擎

### 核心痛点
> 鱼皮在做大数据离线分析时，手写 3000 行 SQL，大量子查询几乎一样但 WHERE/GROUP BY 不同，维护噩梦。

### 解决方案: `@ruleName(params)` 函数调用语法

```json
{
  "main": "select @身高差() from (@学生表(id = 1)) s1, (@学生表(id = 2)) s2",
  "身高差": "(s1.height - s2.height) as 身高差",
  "学生表": "select * from student where id = #{id}"
}
```

| 语法 | 含义 |
|------|------|
| `@ruleName()` | 引用另一个 SQL 片段 |
| `@ruleName(param = value)` | 带参数引用 |
| `@a(xx = @b(yy = 1))` | 嵌套调用 — 子查询作为参数 |
| `#{varName}` | 参数占位符 |
| `"main"` | 入口点 — 从这里开始生成 |

### 生成算法 (~180 行 TypeScript)

```
doGenerateSQL(json)
  → generateSQL("main", context, params)  // 递归展开
    → replaceParams()                      // #{var} 替换
    → replaceSubSql()                      // @rule 展开
      → matchSubQuery()                    // 处理嵌套括号
        → 递归调用 generateSQL()
```

**嵌套括号处理的巧妙之处**:
> 标准正则 `/@name\((.*?)\)/` 无法处理 `@a(xx = @b())` 因为 `(.*?)` 非贪婪匹配到第一个 `)`。解决方案：正则匹配后，手动逐字符遍历，用计数器找到真正的闭合括号。

### 调用树可视化
生成过程中同步构建 `InvokeTreeNode` 树，展示每个节点的替换前/后 SQL。

**设计哲学**: "重逻辑轻页面" — 核心引擎 180 行，UI 只用 Monaco Editor 双栏 + Ant Design。

---

# Part 3: yuindex — 极客浏览器主页 (2,124⭐)

## 从零手写 Web 终端 + 命令系统

### 三模块架构

```
YuTerminal (Web终端UI) ←→ Executor (命令引擎) ←→ Commands (插件式命令集)
```

### 命令定义协议

```typescript
interface CommandType {
  func: string;           // 唯一标识
  name: string;           // 中文名
  alias?: string[];       // 别名
  params?: [...];         // 位置参数
  options: [...];         // 选项参数 (-f, --flag)
  subCommands?: {...};    // 子命令（递归）
  action: (options, terminal) => void;
}
```

### 命令执行流程

```
用户输入 "search 程序员鱼皮 -f github"
  → getopts 解析 → { _: ["程序员鱼皮"], from: "github" }
  → 匹配 searchCommand
  → 根据 from="github" → window.open("https://github.com/search?q=...")
```

### 创意亮点: 浏览器内文件系统

模拟 Linux 文件系统管理收藏链接:
- `ls` `cd` `pwd` `mkdir` `rm` `cp` `mv` `add`
- 扁平化存储 + 路径计算算法
- Pinia + localStorage 持久化

### 功能清单
- 16 个搜索引擎 (baidu/bilibili/github/google...)
- 空间系统 (文件系统模拟)
- 用户系统 (Express + MySQL + Redis)
- 音乐播放 (网易云 API)
- 翻译 (百度翻译)
- 待办/定时器/热搜/摸鱼游戏

---

# Part 4: yu-picture — 智能协同云图库 (1,033⭐)

## DDD 架构 + WebSocket 实时协同 + AI 扩图

### 双架构版本

| 模块 | 架构 |
|------|------|
| `yu-picture-backend` | 传统三层 (Controller → Service → Mapper) |
| `yu-picture-backend-ddd` | **DDD 领域驱动设计** |

### DDD 分层

```
interfaces/     ← Controller + DTO + VO + Assembler
application/    ← 编排领域服务
domain/         ← 核心业务 (picture/space/user 三个聚合)
infrastructure/ ← MySQL + Redis + COS + AI API
shared/         ← Sa-Token + 分片 + WebSocket
```

### 技术亮点

| 特性 | 实现 |
|------|------|
| **动态分表** | ShardingSphere 按 spaceId 分表 `picture_{spaceId}` |
| **AI 扩图** | 阿里云 DashScope Out-painting (异步任务) |
| **以图搜图** | Jsoup 三步链式爬取 |
| **按色搜图** | RGB 欧氏距离算法 |
| **实时协同** | WebSocket + LMAX Disruptor 无锁队列 |
| **多级缓存** | Redis (L2) + Caffeine (L1) |
| **图片处理** | 上传时自动提取尺寸/格式/主色调 |
| **权限** | Sa-Token RBAC + 空间级权限 |

### Disruptor 高性能编辑事件处理

```
Ring Buffer (1024 * 256 = 262,144)
  生产者: PictureEditEventProducer (WebSocket 事件)
  消费者: PictureEditEventWorkHandler (异步处理)
```

---

## 四个项目的技术价值总结

| 项目 | 核心技术点 | 面试/简历价值 |
|------|-----------|-------------|
| **sql-mother** | WebAssembly + 纯前端 SQL 引擎 + 判题算法 | "我用 Wasm 在浏览器中跑了一个完整的数据库" |
| **sql-generator** | 递归 DSL 解析 + 嵌套括号匹配 + 调用树可视化 | "我设计了一个 SQL 模板语言，180 行核心引擎" |
| **yuindex** | 手写 Web 终端 + 命令式插件架构 + 浏览器文件系统 | "我从零实现了一个 Linux 风格的浏览器终端" |
| **yu-picture** | DDD + 动态分表 + WebSocket协同 + Disruptor + AI | "我用 DDD 架构做了一个支持多人实时协同的图库" |
