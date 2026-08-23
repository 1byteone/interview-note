# 简易部署系统 — 小项目

> 用 Shell 脚本实现一个完整的部署流水线：拉取代码 → 构建 → 部署 → 健康检查 → 回滚。这个项目模拟了企业级 CI/CD 的核心流程。

---

## 项目概述

### 需求

实现一个脚本 `deploy-system.sh`，能够：

1. 从 Git 仓库拉取指定分支的代码
2. 使用 Maven 构建 Java 项目
3. 将构建产物部署到目标目录
4. 执行健康检查确认服务正常运行
5. 如果健康检查失败，自动回滚到上一个版本

### 目录结构

```
deploy-system/
├── deploy-system.sh        # 主脚本
├── config/
│   └── services.conf       # 服务配置
├── releases/
│   └── gateway/            # 每次发布版本存档
│       ├── v1.0.0/
│       ├── v1.1.0/
│       └── current -> v1.1.0/   # 当前版本软链接
└── logs/
    └── deploy.log          # 部署日志
```

---

## 配置文件

```bash
# config/services.conf
# 服务配置：格式为 服务名:Git仓库:构建命令:端口:健康检查URL

gateway:git@github.com:example/mall-gateway.git:mvn clean package -DskipTests:8080:http://localhost:8080/actuator/health
user-service:git@github.com:example/mall-user.git:mvn clean package -DskipTests:8081:http://localhost:8081/actuator/health
product-service:git@github.com:example/mall-product.git:mvn clean package -DskipTests:8082:http://localhost:8082/actuator/health
```

---

## 主脚本

```bash
#!/bin/bash
# 文件名: deploy-system.sh
# 用途: 简易部署系统

set -euo pipefail

# 配置
BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
CONFIG_FILE="${BASE_DIR}/config/services.conf"
RELEASES_DIR="${BASE_DIR}/releases"
LOG_FILE="${BASE_DIR}/logs/deploy.log"
WORK_DIR="${BASE_DIR}/work"

# 颜色
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 日志
log() {
    local level=$1
    local msg=$2
    local color=$NC
    case "${level}" in
        INFO)  color=$GREEN ;;
        ERROR) color=$RED ;;
        WARN)  color=$YELLOW ;;
    esac
    echo -e "${color}[$(date '+%Y-%m-%d %H:%M:%S')] [${level}] ${msg}${NC}"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [${level}] ${msg}" >> "${LOG_FILE}"
}

# 检查依赖
check_dependencies() {
    for cmd in git mvn curl; do
        if ! command -v "${cmd}" &>/dev/null; then
            log ERROR "缺少依赖: ${cmd}"
            exit 1
        fi
    done
}

# 读取服务配置
load_service_config() {
    local service_name=$1
    grep "^${service_name}:" "${CONFIG_FILE}" 2>/dev/null || {
        log ERROR "未找到服务配置: ${service_name}"
        exit 1
    }
}

# 拉取代码
pull_code() {
    local repo_url=$1
    local branch=$2
    local target_dir=$3
    
    log INFO "拉取代码: ${repo_url} (分支: ${branch})"
    
    if [ -d "${target_dir}/.git" ]; then
        cd "${target_dir}"
        git fetch origin
        git checkout "${branch}"
        git pull origin "${branch}"
    else
        mkdir -p "${target_dir}"
        git clone -b "${branch}" "${repo_url}" "${target_dir}"
    fi
    
    local commit_id=$(git -C "${target_dir}" rev-parse --short HEAD)
    log INFO "当前 Commit: ${commit_id}"
    echo "${commit_id}"
}

# 构建项目
build_project() {
    local project_dir=$1
    local build_command=$2
    
    log INFO "开始构建..."
    cd "${project_dir}"
    
    if eval "${build_command}"; then
        log INFO "构建成功"
        return 0
    else
        log ERROR "构建失败"
        return 1
    fi
}

# 部署
deploy() {
    local service_name=$1
    local version=$2
    local jar_source=$3
    local target_dir=$4
    
    # 创建版本目录
    local release_dir="${RELEASES_DIR}/${service_name}/${version}"
    mkdir -p "${release_dir}"
    
    # 复制构建产物
    cp "${jar_source}" "${release_dir}/"
    
    # 更新 current 软链接
    ln -sfn "${release_dir}" "${RELEASES_DIR}/${service_name}/current"
    
    # 实际部署到目标目录
    cp "${jar_source}" "${target_dir}/"
    
    log INFO "部署完成: ${version}"
}

# 健康检查
health_check() {
    local url=$1
    local max_retries=$2
    local retry_interval=$3
    
    log INFO "健康检查: ${url}"
    
    for ((i=1; i<=max_retries; i++)); do
        sleep "${retry_interval}"
        
        local http_code=$(curl -s -o /dev/null -w "%{http_code}" "${url}" 2>/dev/null || echo "000")
        local response=$(curl -s "${url}" 2>/dev/null || echo "")
        
        if [ "${http_code}" = "200" ]; then
            log INFO "健康检查通过 (${i}/${max_retries})"
            return 0
        fi
        
        log WARN "健康检查未通过 (${i}/${max_retries}): HTTP ${http_code}"
    done
    
    log ERROR "健康检查失败"
    return 1
}

# 回滚
rollback() {
    local service_name=$1
    local target_dir=$2
    
    log WARN "触发回滚..."
    
    local releases=($(ls -t "${RELEASES_DIR}/${service_name}/" 2>/dev/null || true))
    
    if [ ${#releases[@]} -lt 2 ]; then
        log ERROR "没有可回滚的版本"
        return 1
    fi
    
    # 上一个版本是列表中第二个（第一个是当前版本）
    local previous_version="${releases[1]}"
    log INFO "回滚到: ${previous_version}"
    
    local previous_jar=$(find "${RELEASES_DIR}/${service_name}/${previous_version}" -name "*.jar" | head -1)
    if [ -f "${previous_jar}" ]; then
        cp "${previous_jar}" "${target_dir}/"
        ln -sfn "${RELEASES_DIR}/${service_name}/${previous_version}" "${RELEASES_DIR}/${service_name}/current"
        log INFO "回滚成功"
        return 0
    else
        log ERROR "回滚失败: 找不到 ${previous_version} 的构建产物"
        return 1
    fi
}

# 重启服务
restart_service() {
    local service_name=$1
    
    log INFO "重启服务: ${service_name}"
    
    if systemctl list-units --full -all 2>/dev/null | grep -q "mall-${service_name}"; then
        sudo systemctl restart "mall-${service_name}"
    else
        # 如果没有 systemd 服务，尝试 kill 旧进程并启动
        local pid=$(pgrep -f "mall-${service_name}" 2>/dev/null || true)
        [ -n "${pid}" ] && kill "${pid}" 2>/dev/null || true
        sleep 2
        # 启动新进程（假设 JAR 在 target_dir）
        nohup java -jar "${target_dir}/mall-${service_name}.jar" > /dev/null 2>&1 &
    fi
    
    log INFO "服务已重启"
}

# 主流程
main() {
    local service_name=${1:-}
    local branch=${2:-main}
    local version=${3:-$(date +%Y%m%d_%H%M%S)}
    
    if [ -z "${service_name}" ]; then
        echo "用法: $0 <service-name> [branch] [version]"
        echo "示例: $0 gateway main v1.2.0"
        exit 1
    fi
    
    log INFO "===== 开始部署 ${service_name} ====="
    
    check_dependencies
    
    # 读取配置
    local config=$(load_service_config "${service_name}")
    IFS=':' read -r name repo_url build_cmd port health_url <<< "${config}"
    
    # 工作目录
    local work_dir="${WORK_DIR}/${service_name}"
    local target_dir="/opt/mall/services/${service_name}"
    mkdir -p "${work_dir}" "${target_dir}"
    
    # 1. 拉取代码
    local commit_id=$(pull_code "${repo_url}" "${branch}" "${work_dir}")
    version="${version}-${commit_id}"
    
    # 2. 构建
    if ! build_project "${work_dir}" "${build_cmd}"; then
        log ERROR "构建失败，终止部署"
        exit 1
    fi
    
    # 找到构建产物
    local jar_file=$(find "${work_dir}/target" -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" | head -1)
    if [ -z "${jar_file}" ]; then
        log ERROR "未找到构建产物"
        exit 1
    fi
    
    # 3. 部署
    deploy "${service_name}" "${version}" "${jar_file}" "${target_dir}"
    
    # 4. 重启服务
    restart_service "${service_name}"
    
    # 5. 健康检查
    if health_check "${health_url}" 15 2; then
        log INFO "===== ${service_name} 部署成功 (${version}) ====="
    else
        log ERROR "===== ${service_name} 部署失败，开始回滚 ====="
        rollback "${service_name}" "${target_dir}"
        restart_service "${service_name}"
        health_check "${health_url}" 15 2 || true
    fi
}

main "$@"
```

---

## 使用方式

```bash
# 部署单个服务
./deploy-system.sh gateway main v1.2.0

# 部署所有服务
for svc in gateway user-service product-service; do
    ./deploy-system.sh "${svc}" main
done

# 查看部署日志
tail -f logs/deploy.log

# 查看版本历史
ls -l releases/gateway/
```

---

## 扩展建议

- 添加 **钉钉/飞书通知**：部署成功/失败时发送通知
- 支持 **多环境**：dev/staging/prod 不同配置
- 添加 **压力测试**：部署前先跑一轮压测
- 集成 **SonarQube**：代码质量检查
- 支持 **Docker 部署**：构建 Docker 镜像并推送

---

## 项目要求

实现上述 `deploy-system.sh` 脚本，确保：

1. 所有函数功能完整可用
2. 错误处理完善（set -e、参数校验、失败回滚）
3. 日志输出清晰（颜色区分级别）
4. 代码注释完整

完成后执行 `./deploy-system.sh gateway` 验证基本流程。