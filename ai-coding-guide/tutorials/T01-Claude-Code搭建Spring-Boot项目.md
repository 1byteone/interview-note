# T01: Spring Boot 项目从零搭建

> **[← 教程目录](README.md) | 工具: Claude Code | 时长: ~30min**

---

## Goal

用 Claude Code 从零生成一个**生产级** Spring Boot 微服务项目，包含完整分层、测试、CI、Docker。

## 前置条件

```bash
# 安装 Claude Code
npm install -g @anthropic-ai/claude-code

# 验证安装
claude --version

# 准备一个空目录
mkdir my-spring-boot-app && cd my-spring-boot-app
git init
```

## Step 1: 创建 CLAUDE.md

先创建项目规范文件，Claude Code 每次启动都会读取：

```bash
cat > CLAUDE.md << 'EOF'
# Project Rules

## Tech Stack
- Java 21, Spring Boot 3.3, Maven
- MyBatis-Plus, MySQL 8.0, Redis 7
- JWT 认证, Spring Security

## Architecture
- Controller → Service → Repository 三层
- DTO 与 Entity 严格分离
- 异常统一用 @RestControllerAdvice 处理

## Conventions
- 不使用 Lombok
- 所有公共 API 有 @Tag + @Operation 注解
- 测试用 Testcontainers + JUnit 5
- 每个 Service 方法不超过 30 行

## Build & Test
- 编译: mvn clean compile
- 单模块测试: mvn test -pl <module>
- 全量测试: mvn verify
EOF
```

## Step 2: 启动 Claude Code 并给出生成 Prompt

```bash
claude
```

在 Claude Code 中输入：

```
创建一个用户管理微服务。

# 功能
1. 用户注册（手机号 + 密码）
2. 用户登录（JWT Token）
3. 用户信息 CRUD
4. 分页查询用户列表

# 技术要求
- Spring Boot 3.3 + Java 21
- MyBatis-Plus + MySQL
- Spring Security + JWT
- 所有 API 有 Swagger 文档
- 包含单元测试和集成测试
- 包含 Dockerfile 和 docker-compose.yml

# 流程
1. 先输出项目结构和实施计划
2. 我确认后逐步实现
3. 每完成一个模块运行对应测试
```

## Step 3: 审查 Plan 并确认

Claude Code 会输出类似：

```
## 实施计划

### 模块: user-service

1. pom.xml - Maven 依赖配置
2. application.yml - 数据库/Redis/JWT 配置
3. entity/User.java - 用户实体
4. dto/UserCreateDTO.java - 注册请求
5. dto/UserLoginDTO.java - 登录请求
6. dto/UserResponseDTO.java - 响应
7. mapper/UserMapper.java - MyBatis-Plus Mapper
8. service/UserService.java - 业务逻辑
9. controller/UserController.java - REST API
10. security/JwtTokenProvider.java - JWT 工具
11. security/SecurityConfig.java - 安全配置
12. config/SwaggerConfig.java - API 文档
13. exception/BusinessException.java - 业务异常
14. exception/GlobalExceptionHandler.java - 全局异常
15. test/ - 单元测试 + 集成测试
16. Dockerfile + docker-compose.yml

预计 Token: ~15,000
```

审查计划，确认后 Claude Code 开始逐步生成代码。

## Step 4: 验证生成结果

```bash
# 编译检查
mvn clean compile

# 运行测试（需要 Docker for Testcontainers）
mvn test

# 启动应用
mvn spring-boot:run -pl user-service

# 验证 API
curl -X POST http://localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800138000","password":"test123"}'
```

## Step 5: 检查 Claude Code 的修改

```bash
# 查看所有修改的文件
git status

# 查看具体变更
git diff

# 检查生成的文件数量
find . -name "*.java" | wc -l
find . -name "*.xml" | wc -l
```

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| 编译失败 | 把错误信息粘贴给 Claude Code: `修复编译错误: <粘贴错误>` |
| 测试失败 | `分析测试失败原因并修复: <粘贴错误>` |
| 生成了 Lombok | 在 CLAUDE.md 中强化: `绝对禁止使用 Lombok` |
| 分层不清晰 | `重构 Controller，把业务逻辑移到 Service 层` |

## 延伸

- → [T02: CLAUDE.md 企业级配置](T02-Claude-Code企业级CLAUDE-MD配置.md)
- → [T11: Cursor Rules 实战](T11-Cursor-Rules实战.md)
