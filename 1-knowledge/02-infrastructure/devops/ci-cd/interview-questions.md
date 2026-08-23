# CI/CD 面试题大全

## 📚 知识体系

```
CI/CD 核心概念
├── 持续集成 (CI)
│   ├── 代码提交触发构建
│   ├── 自动编译 & 单元测试
│   ├── 代码质量检查 (SonarQube)
│   └── 构建产物管理
├── 持续交付 (CD)
│   ├── 自动部署到测试环境
│   ├── 自动集成测试
│   └── 手动/自动发布审批
├── 持续部署 (CD)
│   ├── 自动部署到生产环境
│   ├── 灰度发布 / 蓝绿部署
│   └── 回滚机制
└── 工具链
    ├── Jenkins / GitLab CI / GitHub Actions
    ├── Maven / Gradle
    ├── Docker / Docker Compose
    ├── SonarQube
    ├── Nexus / Harbor
    └── K8s / Helm
```

---

## 🎯 Level 1：基础题

### 1. 什么是 CI/CD？为什么需要？
**答案**：

| 概念 | 说明 |
|------|------|
| **CI（持续集成）** | 频繁合并代码到主干，每次提交自动构建+测试 |
| **CD（持续交付）** | CI 基础上，自动部署到测试环境，随时可发布 |
| **CD（持续部署）** | 自动通过测试后直接部署到生产环境 |

**价值**：
1. 快速发现集成问题（几分钟内）
2. 减少手动操作错误
3. 缩短发布周期（从月到天/小时）
4. 可重复的构建和部署流程

### 2. CI/CD 的典型流程？
**答案**：

```text
代码提交 (git push)
    ↓
代码扫描 (SonarQube) + 依赖检查
    ↓
编译构建 (Maven/Gradle)
    ↓
单元测试 (JUnit) + 集成测试
    ↓
构建 Docker 镜像
    ↓
推送到镜像仓库 (Harbor/Docker Hub)
    ↓
部署到测试环境 (Docker/K8s)
    ↓
自动化测试 (API/UI 测试)
    ↓
审批 → 发布到生产环境
    ↓
健康检查 + 监控告警
```

---

## 🎯 Level 2：进阶题

### 3. GitLab CI 的 pipeline 如何配置？
**答案**：

```yaml
# .gitlab-ci.yml
stages:
  - build
  - test
  - docker
  - deploy

variables:
  MAVEN_OPTS: "-Xmx1024m"

build:
  stage: build
  image: maven:3.8-openjdk-17
  script:
    - mvn clean compile -DskipTests
  artifacts:
    paths:
      - target/*.jar

test:
  stage: test
  image: maven:3.8-openjdk-17
  script:
    - mvn test
  coverage: '/Line coverage: (\d+\.\d+%)/'

docker:
  stage: docker
  image: docker:latest
  services:
    - docker:dind
  script:
    - docker build -t registry.example.com/app:$CI_COMMIT_SHORT_SHA .
    - docker push registry.example.com/app:$CI_COMMIT_SHORT_SHA
  only:
    - main

deploy:
  stage: deploy
  script:
    - kubectl set image deployment/app app=registry.example.com/app:$CI_COMMIT_SHORT_SHA
    - kubectl rollout status deployment/app
  only:
    - main
  when: manual  # 手动触发
```

### 4. Jenkins Pipeline 和 GitLab CI 的区别？
**答案**：

| 特性 | Jenkins | GitLab CI |
|------|---------|-----------|
| 安装 | 需自建服务 | 内置在 GitLab |
| 配置 | Jenkinsfile（Groovy） | .gitlab-ci.yml（YAML） |
| 学习曲线 | 较陡 | 较平缓 |
| 插件生态 | 丰富 | 一般 |
| 天然集成 | 需配置 | 与 GitLab 集成 |
| 维护成本 | 较高 | 低（GitLab 自带） |
| 私有化 | 成熟 | 支持 |

---

## 🎯 Level 3：高级题

### 5. 蓝绿部署和灰度发布的区别？
**答案**：

**蓝绿部署**：
```
蓝环境（旧版本）→ 切换 → 绿环境（新版本）
```
- 两套环境完整，切换瞬间完成
- 回滚只需切回

**灰度发布**：
```
旧版本 90% 流量 + 新版本 10% 流量
  ↓ 逐步调整比例
旧版本 0% 流量 + 新版本 100% 流量
```
- 逐步放量，实时监控
- 有问题只影响小部分用户

**选择**：蓝绿适配大版本切换，灰度适配渐进式更新。

### 6. 如何设计一个高可用的 CI/CD 系统？
**答案**：

**要点**：
1. **构建节点多副本**：Jenkins Agent / GitLab Runner 集群
2. **制品仓库高可用**：Nexus / Harbor 集群
3. **镜像仓库复制**：跨地域同步
4. **回滚机制**：保留历史版本，一键回滚
5. **审批流程**：生产环境部署需审批
6. **监控告警**：构建失败、部署异常即时通知

---

## 📖 学习资源

### 推荐项目
- [Awesome CI/CD](https://github.com/ci-and-cd/awesome-ci-cd)
- [GitLab CI 官方文档](https://docs.gitlab.com/ee/ci/)
- [Jenkins 官方文档](https://www.jenkins.io/doc/)

### 最佳实践
1. 多条流水线分离（开发/测试/生产）
2. 构建产物不重复（Docker 镜像缓存）
3. 敏感信息用 CI/CD 变量（不写死在代码）
4. 部署后自动健康检查
5. 保留构建日志和历史版本（回滚用）