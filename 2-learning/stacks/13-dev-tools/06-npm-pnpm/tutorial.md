# npm vs pnpm — Node.js 包管理器进阶教程

> **定位**：面向 JS / TS / Node.js / AI Agent 开发者的包管理器系统性教程
> **核心主题**：npm 与 pnpm 的职责、底层存储机制差异、锁文件、Monorepo 与发布链路
> **适用**：GitHub README 配图 / 技术博客 / 面试准备 / AI 工程文档

---

## 核心认知（先读这一段）

**npm 和 pnpm** 都是 Node.js 生态中的包管理器，核心职责都是**安装、更新、删除、管理项目依赖**；最大的区别在于**"依赖如何存储和安装"**。

> **一句话记忆：npm 和 pnpm 都是 Node.js 包管理器，都可以从 npm Registry 安装 npm 包；npm 更传统、兼容性广，而 pnpm 通过 Store + 链接机制减少依赖重复，并且特别适合大型项目和 Monorepo。**

---

## 技术全景图

![npm vs pnpm 企业级技术全景信息图](images/npm-vs-pnpm-ecosystem.png)

> 📐 1536×1024 PNG | 2.4 MB | 原图未压缩
>
> 这张图完整呈现了本文 14 个章节的要点：npm/pnpm 双工作流、核心存储差异、node_modules 结构、锁文件、pnpm Workspace、npm 包发布链路，以及 `npm Registry ≠ npm` 的区分。

---

## 第 1 章：npm 是什么？

**npm（Node Package Manager）** 是 Node.js 生态最传统、最广泛使用的包管理器。

```bash
npm install axios
```

npm 的工作流程：

```text
package.json
      ↓
npm install
      ↓
解析依赖
      ↓
下载依赖
      ↓
node_modules
```

典型项目结构：

```text
my-project/
├── package.json
├── package-lock.json
├── src/
└── node_modules/
    ├── axios/
    ├── express/
    └── ...
```

常用命令：

```bash
npm install
npm install axios
npm install -D typescript

npm uninstall axios

npm update

npm run dev
npm run build

npm list
```

---

## 第 2 章：pnpm 是什么？

**pnpm** 也是 Node.js 包管理器，它与 npm 的目标基本相同：

```text
管理项目依赖
安装 npm 包
执行 scripts
管理版本
生成锁文件
```

```bash
pnpm install
pnpm add axios
pnpm add -D typescript
```

pnpm 最大的特点：

> **通过全局内容寻址存储 + 硬链接/符号链接机制，减少重复依赖文件。**

这是 pnpm 最核心的优势。

---

## 第 3 章：npm 与 pnpm 最大区别

假设有三个项目都用到 React 19：

```text
项目 A
└── React 19

项目 B
└── React 19

项目 C
└── React 19
```

### npm 的传统思路

每个项目的 `node_modules` 中都可能存在自己的依赖副本：

```text
项目A
└── node_modules
    └── react

项目B
└── node_modules
    └── react

项目C
└── node_modules
    └── react
```

如果多个项目使用相同版本，磁盘上可能产生重复内容。

### pnpm 的思路

pnpm 维护一个全局内容存储（Store），项目中的 `node_modules` 通过链接引用：

```text
                pnpm Store
                    │
          ┌─────────┼─────────┐
          ↓         ↓         ↓
       react@19   axios@1   vite@8
          │
     ┌────┼────┐
     ↓    ↓    ↓
    项目A 项目B 项目C
```

因此：

> **同一个包版本尽可能只保存一份实际内容。**

---

## 第 4 章：node_modules 结构差异

npm 的扁平结构：

```text
node_modules/
├── axios/
├── express/
├── react/
├── lodash/
└── ...
```

pnpm 的结构：

```text
node_modules/
├── .pnpm/
│   ├── axios@...
│   ├── express@...
│   ├── react@...
│   └── ...
│
├── axios -> .pnpm/...
├── express -> .pnpm/...
└── react -> .pnpm/...
```

**`node_modules/.pnpm/` 就是 pnpm 很明显的特征。**

---

## 第 5 章：为什么 pnpm 通常更省磁盘？

```text
项目 A → React 19
项目 B → React 19
项目 C → React 19
项目 D → React 19
```

传统思路（可能重复物理存储）：

```text
项目A/react
项目B/react
项目C/react
项目D/react
```

pnpm（Store 中只存一份）：

```text
                  pnpm Store
                     │
                  react@19
                     │
        ┌────────────┼────────────┐
        ↓            ↓            ↓
       A项目         B项目         C项目
                     │
                     ↓
                    D项目
```

在以下场景，pnpm 优势明显：

- 前端开发
- Monorepo
- 多个 Node 项目
- 大型 workspace

---

## 第 6 章：锁文件不同（重要！）

npm：

```text
package.json
package-lock.json
```

pnpm：

```text
package.json
pnpm-lock.yaml
```

项目结构示例：

```text
my-project/
├── package.json
├── pnpm-lock.yaml
└── node_modules/
```

**实践原则**：一个项目如果原本使用 `pnpm-lock.yaml`，应继续使用 `pnpm install`，不要随便改成 `npm install`——这可能导致依赖解析结果、锁文件和 `node_modules` 状态发生变化。

---

## 第 7 章：命令对照表

| 功能     | npm                  | pnpm              |
| ------ | -------------------- | ----------------- |
| 安装全部依赖 | `npm install`        | `pnpm install`    |
| 安装生产依赖 | `npm install xxx`    | `pnpm add xxx`    |
| 安装开发依赖 | `npm install -D xxx` | `pnpm add -D xxx` |
| 删除依赖   | `npm uninstall xxx`  | `pnpm remove xxx` |
| 执行脚本   | `npm run dev`        | `pnpm run dev`    |
| 构建     | `npm run build`      | `pnpm run build`  |
| 更新依赖   | `npm update`         | `pnpm update`     |
| 查看依赖   | `npm list`           | `pnpm list`       |

记忆要点：`npm install xxx` ↔ `pnpm add xxx`，其余一一对应。

---

## 第 8 章：pnpm 为什么特别适合 Monorepo？

```text
my-project/
│
├── pnpm-workspace.yaml
│
├── apps/
│   ├── web/
│   └── admin/
│
└── packages/
    ├── ui/
    ├── utils/
    └── config/
```

```text
                 pnpm Workspace
                       │
          ┌────────────┼────────────┐
          ↓            ↓            ↓
        web          admin         ui
          │            │            │
          └────────────┼────────────┘
                       ↓
                     utils
```

**pnpm + pnpm workspace** 是很多现代 JS / TS Monorepo 项目的标配：

```text
apps/
packages/
```

---

## 第 9 章：npm 与 pnpm 的联系

不要把两者理解成完全不同的生态：

```text
                  npm Registry
                       │
        ┌──────────────┴──────────────┐
        ↓                             ↓
       npm                           pnpm
        │                             │
        └──────────────┬──────────────┘
                       ↓
                  npm packages
                       ↓
              package.json
```

即：

```bash
npm install lodash     # 和
pnpm add lodash        # 安装的是同一个 lodash 包
```

> **npm / pnpm 是"包管理工具"，npm Registry 是"包仓库生态"。这三个概念不要混淆。**

---

## 第 10 章：npm Registry ≠ npm

之前你使用 npm 时可能见过：

```text
https://registry.npmjs.org/
```

这是 **npm Registry**，它相当于**软件包仓库**；而 **npm** 是**包管理客户端**。

```text
              npm Registry
              ┌─────────────┐
              │ axios       │
              │ react       │
              │ vue         │
              │ express     │
              │ vite        │
              └──────┬──────┘
                     ↑
            ┌────────┴────────┐
            │                 │
           npm               pnpm
        包管理器            包管理器
```

pnpm 并不是另一个"npm 仓库"。

---

## 第 11 章：性能区别

pnpm 优势明显的场景：

```text
多个项目
    ↓
大量重复依赖
    ↓
pnpm Store
    ↓
复用已有依赖
```

### npm 安装流程

```text
安装项目
    ↓
解析依赖
    ↓
下载
    ↓
node_modules
```

### pnpm 安装流程

```text
安装项目
    ↓
解析依赖
    ↓
检查 pnpm Store
    ↓
已有内容 → 复用
没有 → 下载
    ↓
链接到项目
```

pnpm 通常能：

- ✅ 节省磁盘空间
- ✅ 减少重复下载
- ✅ 很多场景下安装更快
- ✅ 对 Monorepo 更友好

**⚠️ 但实际速度仍取决于**：网络、Registry、缓存、lockfile、项目规模、磁盘性能。

> **不要简单理解成 "pnpm 永远比 npm 快"。**

---

## 第 12 章：依赖结构更严格（隐藏价值）

pnpm 的 node_modules 结构能帮企业暴露不规范的依赖问题。

```json
{
  "dependencies": {
    "express": "^5.0.0"
  }
}
```

但代码里偷偷用了未声明的依赖：

```javascript
import lodash from "lodash";  // lodash 只是某个间接依赖
```

- npm 扁平化结构下：可能"碰巧能运行"（隐式依赖）
- pnpm 严格结构下：更容易暴露**你使用了没有在 package.json 中声明的依赖**

**对大型工程非常有价值。**

---

## 第 13 章：关系全景记忆图

```text
                         Node.js 生态
                              │
                     npm Registry
                              │
          ┌───────────────────┴───────────────────┐
          │                                       │
         npm                                     pnpm
     包管理器                                   包管理器
          │                                       │
          ↓                                       ↓
     node_modules                            node_modules
          │                                       │
          ↓                                       ↓
    npm package-lock.json                  pnpm-lock.yaml
```

---

## 第 14 章：如果你做 JS / AI Agent 开发

结合你当前的技能栈（AI Agent / Claude Code / Codex / Skills / GitHub 开源 / TypeScript），建议掌握：

```text
Node.js
   │
   ├── npm
   ├── pnpm
   ├── package.json
   ├── package-lock.json
   ├── pnpm-lock.yaml
   ├── node_modules
   ├── npm Registry
   ├── npm publish
   └── pnpm Workspace
```

以后发布自己的包（`my-ai-skill` / `my-dsh-plugin` / `my-cli` / `my-agent-tool`）会涉及完整链路：

```text
package.json
      ↓
npm / pnpm
      ↓
npm Registry
      ↓
npm publish
      ↓
其他开发者
      ↓
npm install / pnpm add
```

---

## 附录 A：总图记忆

```text
                         Node.js 生态
                              │
                        npm Registry
                     （包的公共仓库）
                              │
              ┌───────────────┴───────────────┐
              ↓                               ↓
             npm                             pnpm
         包管理器                          包管理器
              │                               │
      ┌───────┴───────┐               ┌───────┴────────┐
      ↓               ↓               ↓                ↓
 package.json    package-lock.json  package.json   pnpm-lock.yaml
      │                               │
      ↓                               ↓
 node_modules                    pnpm Store
                                      │
                                      ↓
                               链接 / 复用依赖
                                      │
                                      ↓
                              项目 node_modules
```

> **一句话记忆**
>
> > **npm 和 pnpm 都是 Node.js 包管理器，都可以从 npm Registry 安装 npm 包；npm 更传统、兼容性广，而 pnpm 通过 Store + 链接机制减少依赖重复，并且特别适合大型项目和 Monorepo。**

**下一步建议**：如果你正在学习 **"npm 发布自己的 npm 包"**，最值得搞清楚的是 **`package.json` → `npm login` → `npm publish` → Registry → `npm install` / `pnpm add`** 这一整条链路。

---

## 附录 B：教程知识点清单

| 章节 | 核心知识点 | 掌握度 |
|------|-----------|--------|
| 1 | npm 是什么、工作流、常用命令 | ⭐⭐⭐ |
| 2 | pnpm 是什么、Store + Link 机制 | ⭐⭐⭐ |
| 3 | 依赖存储的核心区别 | ⭐⭐⭐ |
| 4 | node_modules 结构差异（`.pnpm/` 特征） | ⭐⭐⭐ |
| 5 | 磁盘节省原理 | ⭐⭐ |
| 6 | 锁文件不同、不得混用 | ⭐⭐⭐ |
| 7 | 命令对照表 | ⭐⭐⭐ |
| 8 | Monorepo / Workspace 优势 | ⭐⭐⭐ |
| 9 | npm 与 pnpm 同源 npm Registry | ⭐⭐ |
| 10 | npm Registry ≠ npm | ⭐⭐⭐ |
| 11 | 性能区别与前提条件 | ⭐⭐ |
| 12 | 严格依赖结构暴露隐式依赖 | ⭐⭐⭐ |
| 13 | 关系全景记忆图 | ⭐⭐⭐ |
| 14 | AI Agent 开发知识路线 | ⭐⭐ |