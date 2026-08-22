# Python 学习资源推荐

> 按"入门 → 进阶 → 实战 → 深挖"四个阶段整理，全部免费或资源丰富。

---

## 一、官方文档（最高优先级）

| 资源 | 说明 |
|---|---|
| [Python 官方教程](https://docs.python.org/zh-cn/3/tutorial/) | 官方中文教程，权威且系统 |
| [Python 标准库文档](https://docs.python.org/zh-cn/3/library/) | 排查 API 用法的第一选择 |
| [PEP 8 风格指南](https://peps.python.org/pep-0008/) | 代码风格规范（中文翻译版见各类博客） |
| [Python 3.12 新特性](https://docs.python.org/zh-cn/3/whatsnew/) | 版本演进追踪（3.13 free-threading 值得关注） |
| [Python Tutor](https://pythontutor.com/) | 可视化一步步执行代码，理解内存与变量 |

---

## 二、入门到进阶

| 资源 | 类型 | 说明 |
|---|---|---|
| [Python Crash Course 2E](https://ehmatthes.github.io/pcc_2e/) | 书（免费网页版） | 经典入门书，项目驱动 |
| [Automate the Boring Stuff](https://automatetheboringstuff.com/) | 书（免费网页版） | 用 Python 自动化办公任务 |
| [Python 数据结构与算法](https://github.com/TheAlgorithms/Python) | GitHub 仓库 | 大量算法实现的 Python 版 |
| [Real Python](https://realpython.com/) | 教程站 | 免费高质量文章，覆盖所有进阶主题 |
| [Effective Python](https://effectivepython.com/) | 书 | 95 个具体写法建议，面试加分 |

---

## 三、异步编程专项

| 资源 | 说明 |
|---|---|
| [asyncio 官方文档](https://docs.python.org/zh-cn/3/library/asyncio.html) | 权威 API 参考 |
| [Async IO in Python: A Complete Walkthrough](https://realpython.com/async-io-python/) | Real Python 完整异步教程 |
| [uvloop](https://github.com/MagicStack/uvloop) | 更快的 asyncio 事件循环替代品 |
| [异步生态清单](https://github.com/timofurrer/awesome-asyncio) | 异步库大全 |

---

## 四、AI 生态（与本技术栈衔接）

| 资源 | 说明 |
|---|---|
| [NumPy 官方快速入门](https://numpy.org/doc/stable/user/quickstart.html) | 数值计算基础 |
| [Pandas 10 分钟入门](https://pandas.pydata.org/docs/user_guide/10min.html) | 数据处理速成 |
| [Hugging Face 课程](https://huggingface.co/learn) | 开源模型 + Transformer 实战 |
| [LangChain 官方文档](https://python.langchain.com/docs) | 后续 14-langchain 技术栈的主力参考 |

---

## 五、调试、测试与工程化

| 资源 | 说明 |
|---|---|
| [pytest 官方教程](https://docs.pytest.org/en/stable/) | 测试框架完整指南 |
| [Pydantic v2 文档](https://docs.pydantic.dev/) | 数据校验核心参考 |
| [mypy 文档](https://mypy.readthedocs.io/) | 类型检查配置 |
| [PDB 调试](https://docs.python.org/zh-cn/3/library/pdb.html) | 断点调试入门 |

---

## 六、社区与资讯

| 资源 | 说明 |
|---|---|
| [/r/Python](https://www.reddit.com/r/Python/) | 海外 Python 社区 |
| [Python 中文社区](https://www.python-cn.org/) | 中文讨论 |
| [PyPI](https://pypi.org/) | 官方包仓库（查包名与版本） |
| [Awesome Python](https://github.com/vinta/awesome-python) | Python 生态精选清单 |

---

## 学习建议

1. **一本好书 + 官方文档**：别囤课程，深入一个系统学完
2. **每天写半小时代码**：语法靠"用"才能内化
3. **学 AI 就用 Python**：看完本技术栈后直接进入 **05-fastapi** 和 **14-langchain**，在实战中巩固
4. **读源码**：至少精读一个库的源码（推荐 `requests` 或 `asyncio` 的 `Task` 相关）
5. **LeetCode 刷 Python 版**：顺手练习语法与算法

> 面试记忆要点已浓缩在 [05-interview/quick-revision.md](05-interview/quick-revision.md)，冲刺前过一遍即可。