# npm vs pnpm — Node.js 包管理器

> 面向 JS / TS / Node.js / AI Agent 开发者的包管理器系统性教程

---

## 📖 教程入口

**[→ 阅读完整教程：npm vs pnpm 进阶教程](tutorial.md)**

覆盖 14 章：

1. npm 是什么
2. pnpm 是什么
3. 存储机制核心区别
4. node_modules 结构差异
5. 磁盘节省原理
6. 锁文件不同
7. 命令对照表
8. Monorepo 优势
9. 两者同源联系
10. npm Registry ≠ npm
11. 性能区别
12. 严格依赖结构
13. 关系全景记忆图
14. AI Agent 开发知识路线

---

## 🖼️ 技术全景图

![npm vs pnpm 企业级技术全景信息图](images/npm-vs-pnpm-ecosystem.png)

> 📐 1536×1024 PNG | 2.4 MB | 原图未压缩

---

## 🎯 核心认知

**npm 和 pnpm** 都是 Node.js 生态中的包管理器，核心职责都是**安装、更新、删除、管理项目依赖**；最大的区别在于**"依赖如何存储和安装"**。

> **一句话记忆：npm 和 pnpm 都是 Node.js 包管理器，都可以从 npm Registry 安装 npm 包；npm 更传统、兼容性广，而 pnpm 通过 Store + 链接机制减少依赖重复，并且特别适合大型项目和 Monorepo。**

---

## 🔑 快速速查

| 功能     | npm                  | pnpm              |
| ------ | -------------------- | ----------------- |
| 安装全部依赖 | `npm install`        | `pnpm install`    |
| 安装生产依赖 | `npm install xxx`    | `pnpm add xxx`    |
| 安装开发依赖 | `npm install -D xxx` | `pnpm add -D xxx` |
| 删除依赖   | `npm uninstall xxx`  | `pnpm remove xxx` |
| 执行脚本   | `npm run dev`        | `pnpm run dev`    |
| 构建     | `npm run build`      | `pnpm run build`  |
| 锁文件    | `package-lock.json`  | `pnpm-lock.yaml`  |
| 结构特征   | 扁平 node_modules    | `.pnpm/` + 符号链接 |

---

## 📦 图片资源

| 文件 | 说明 |
|------|------|
| `images/npm-vs-pnpm-ecosystem.png` | 全景信息图（1536×1024，2.4MB 原图） |

### 重新生成图片（可选）

如需用 GPT Image / Midjourney / Flux / Gemini Image 重新生成同款风格图片，Prompt 已归档在 **[`assets/prompts/npm-vs-pnpm-infographic.md`](assets/prompts/npm-vs-pnpm-infographic.md)**。