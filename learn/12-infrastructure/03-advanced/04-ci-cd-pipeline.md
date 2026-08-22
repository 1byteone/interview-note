# CI/CD 流水线 — 自动化构建 · 测试 · 部署

> 🎯 进阶路线 · 预计阅读时间：40 分钟
> 目标：掌握 CI/CD 全流程设计与主流工具（GitHub Actions、GitLab CI、Jenkins），理解蓝绿/金丝雀/滚动更新部署策略，并能根据项目阶段选择合适方案。

---

## 一、CI/CD 全流程

CI/CD 的核心理念是**自动化**，将代码从提交到上线的每一个环节串联起来，减少人工操作带来的错误与延迟。

```
代码提交 ──► 代码检查 ──► 构建 ──► 测试 ──► 制品存储 ──► 部署 ──► 健康检查
  │           │           │        │         │          │         │
  │          ESLint      Maven   JUnit   Docker Hub   K8s apply   Readiness
  │          Checkstyle  Build   Integ.  /Nexus       /Helm       Probe
  │                                          │                     │
  └── 触发 CI                              └── 触发 CD            └── 回滚（可选）
```

### 1.1 各阶段说明

| 阶段 | 目的 | 典型工具 | 时间 |
|------|------|----------|------|
| 代码检查（Lint） | 保证代码风格与质量 | ESLint、Checkstyle、SpotBugs | 秒级 |
| 构建（Build） | 编译源码，生成制品 | Maven、Gradle、npm、Docker | 秒-分钟 |
| 测试（Test） | 验证功能正确性 | JUnit、Mockito、Postman、Selenium | 分钟级 |
| 制品存储 | 安全保存构建产物 | Docker Hub、Nexus、Harbor、S3 | 秒级 |
| 部署（Deploy） | 将制品发布到环境 | K8s、Helm、Ansible、Terraform | 分钟级 |
| 健康检查 | 验证部署成功 | Actuator Readiness、Prometheus | 秒级 |

### 1.2 分支策略与 CI/CD 联动

常用 Git Flow 变体：

```
main ────────────────┬─── 生产环境（自动部署）
                     │
release/1.2.0 ───────┼─── 预发布环境（自动部署 + 集成测试）
                     │
develop ─────────────┼─── 开发环境（自动部署）
                     │
feature/xxx ─────────┘─── PR 触发 CI 检查（不部署）
```

---

## 二、GitHub Actions 配置示例

```yaml
# .github/workflows/mall-ci.yml
name: Mall CI Pipeline

on:
  push:
    branches: [main, develop, release/*]
  pull_request:
    branches: [main]          # PR 到 main 时也触发

jobs:
  # Job 1: 代码质量检查
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: 设置 JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven
      - name: 代码风格检查
        run: mvn checkstyle:check

  # Job 2: 构建 + 测试（依赖 lint 通过）
  build-and-test:
    needs: lint
    runs-on: ubuntu-latest
    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: test
          MYSQL_DATABASE: mall_test
        ports:
          - 3306:3306
      redis:
        image: redis:7
        ports:
          - 6379:6379
    steps:
      - uses: actions/checkout@v4
      - name: 设置 JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven
      - name: 跑单元测试 + 集成测试
        run: mvn verify -P integration-test
      - name: 构建 Docker 镜像
        run: |
          docker build -t mall-order-service:${{ github.sha }} .
          docker tag mall-order-service:${{ github.sha }} \
            registry.cn-hangzhou.aliyuncs.com/mall/order-service:${{ github.sha }}

  # Job 3: 部署到开发环境
  deploy-dev:
    needs: build-and-test
    if: github.ref == 'refs/heads/develop'
    runs-on: ubuntu-latest
    steps:
      - name: 部署到 K8s 开发环境
        run: |
          kubectl set image deployment/order-service \
            order-service=registry.cn-hangzhou.aliyuncs.com/mall/order-service:${{ github.sha }}
```

---

## 三、GitLab CI 配置示例

```yaml
# .gitlab-ci.yml
stages:
  - lint
  - test
  - build
  - deploy

variables:
  MAVEN_OPTS: "-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository"

cache:
  paths:
    - .m2/repository/

lint:
  stage: lint
  image: maven:3.9-eclipse-temurin-17
  script:
    - mvn checkstyle:check pmd:pmd

test:
  stage: test
  image: maven:3.9-eclipse-temurin-17
  services:
    - name: mysql:8.0
      alias: mysql
    - name: redis:7
      alias: redis
  script:
    - mvn verify -P integration-test
  artifacts:
    paths:
      - target/surefire-reports/
    reports:
      junit: target/surefire-reports/*.xml

build:
  stage: build
  image: docker:27
  services:
    - docker:dind
  script:
    - docker build -t $CI_REGISTRY_IMAGE:$CI_COMMIT_SHA .
    - docker push $CI_REGISTRY_IMAGE:$CI_COMMIT_SHA
  only:
    - main
    - develop

deploy-dev:
  stage: deploy
  image: bitnami/kubectl:latest
  script:
    - kubectl set image deployment/order-service
      order-service=$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA
  environment:
    name: dev
  only:
    - develop
```

---

## 四、Jenkins 经典方案（Pipeline as Code）

```groovy
// Jenkinsfile
pipeline {
    agent any

    tools {
        maven 'Maven-3.9'
        jdk 'JDK-17'
    }

    environment {
        DOCKER_REGISTRY = 'registry.cn-hangzhou.aliyuncs.com/mall'
        K8S_NAMESPACE   = 'mall-dev'
    }

    stages {
        stage('代码检出') {
            steps {
                checkout scm
            }
        }

        stage('单元测试 + 集成测试') {
            steps {
                sh 'mvn verify -P integration-test -DskipITs=false'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('代码质量') {
            steps {
                sh 'mvn sonar:sonar -Dsonar.host.url=$SONAR_HOST'
            }
        }

        stage('Docker 构建') {
            steps {
                sh """
                    docker build -t ${DOCKER_REGISTRY}/order-service:${BUILD_NUMBER} .
                    docker push ${DOCKER_REGISTRY}/order-service:${BUILD_NUMBER}
                """
            }
        }

        stage('部署到开发环境') {
            when {
                branch 'develop'
            }
            steps {
                sh """
                    kubectl set image deployment/order-service \
                        order-service=${DOCKER_REGISTRY}/order-service:${BUILD_NUMBER} \
                        -n ${K8S_NAMESPACE}
                """
            }
        }
    }

    post {
        failure {
            // 构建失败通知
            emailext(
                subject: "构建失败: ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
                body: "请查看 ${env.BUILD_URL} 获取详细信息",
                to: 'team@mall.com'
            )
        }
    }
}
```

### Jenkins 与 GitHub Actions/GitLab CI 对比

| 特性 | GitHub Actions | GitLab CI | Jenkins |
|------|---------------|-----------|---------|
| 托管/自建 | 托管 | 两者均可 | 自建为主 |
| 配置复杂度 | 低 | 低 | 中-高 |
| 插件生态 | 丰富（Marketplace） | 中等 | 极丰富 |
| 可观测性 | 内置日志 | 内置日志 | 需额外配置 |
| 适用团队 | 小型-中型 | 中型 | 中型-大型 |

---

## 五、部署策略对比

### 5.1 蓝绿部署（Blue/Green）

同时维护两套完全相同的环境（蓝 = 当前生产，绿 = 新版本），流量通过负载均衡器切换。

```
用户 ──► 负载均衡器 ──► 蓝环境（v1，当前生产）
                     ──► 绿环境（v2，新版本，验证后切换）
```

**优点**：切换瞬间完成，回滚极快（切回蓝环境即可）；**缺点**：资源成本翻倍。

### 5.2 金丝雀发布（Canary）

先让少量用户（如 5%）使用新版本，观察无异常后逐步扩大范围，直至全量。

```
用户 ──► 负载均衡器 ──► v1（95% 流量）
                     ──► v2（5% 流量，金丝雀）
```

**优点**：风险极低，可充分验证；**缺点**：部署周期长，需流量治理能力（如 Istio、Nginx 权重）。

### 5.3 滚动更新（Rolling Update）

逐步替换 Pod 实例，每次替换一部分，过程中新旧版本共存。

```
v1 v1 v1 v1 v1  ──►  v2 v1 v1 v1 v1  ──►  ...  ──►  v2 v2 v2 v2 v2
```

**优点**：零停机、资源利用率高；**缺点**：回滚慢（需要逐 Pod 回退），多版本共存期间兼容性需注意。

### 5.4 对比总结

| 策略 | 风险 | 回滚速度 | 资源成本 | 适用场景 |
|------|------|----------|----------|----------|
| 蓝绿部署 | 低 | 瞬间 | 高（2 倍） | 关键业务、金融系统 |
| 金丝雀发布 | 最低 | 快（切回权重） | 低 | 大版本变更、重大功能上线 |
| 滚动更新 | 中 | 慢（逐 Pod） | 低 | 日常小版本迭代 |

### 5.5 选择建议

- **初创/小团队**：滚动更新 + 健康检查，基础设施简单、免额外资源；
- **中型团队（日活百万）**：金丝雀发布 + 蓝绿回滚兜底，兼顾安全与成本；
- **大型/金融团队**：蓝绿为主 + 金丝雀灰度验证，双保险。

---

## 六、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| CI/CD 全流程有哪些阶段？ | 代码提交 → Lint → 构建 → 测试 → 制品存储 → 部署 → 健康检查 |
| GitHub Actions 和 Jenkins 选哪个？ | 小团队用 GitHub Actions（零运维），大团队用 Jenkins（灵活、插件丰富） |
| 蓝绿部署和金丝雀发布区别？ | 蓝绿是两套环境瞬间切换，金丝雀是逐步扩大流量比例 |
| 滚动更新有什么缺点？ | 回滚慢，多版本共存期间需要兼容性处理 |
| 部署后怎么验证成功？ | 健康检查（Readiness Probe） + 监控指标（错误率、P99 延迟） |
| Pipeline as Code 的好处？ | 版本化、可评审、可审计，配置即代码 |