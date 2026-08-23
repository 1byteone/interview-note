#!/bin/bash
# ============================================
# 生产环境部署脚本（Spring Boot 示例）
# 流程：拉取代码 → Maven 构建 → 备份旧版 → 部署 → 健康检查 → 失败回滚
# 用法：bash 01_deploy_script.sh
# ============================================

set -euo pipefail  # 严格模式：出错退出、未定义变量报错、管道中断

# ---------- 配置区（按需修改） ----------
APP_NAME="demo-app"                        # 应用名称
GIT_REPO="git@github.com:your/repo.git"    # Git 仓库地址
GIT_BRANCH="main"                          # 部署分支
WORKSPACE="/opt/build/${APP_NAME}"         # 构建目录
DEPLOY_DIR="/opt/apps/${APP_NAME}"         # 部署目录
BACKUP_DIR="/opt/backups/${APP_NAME}"      # 备份目录
JAR_NAME="${APP_NAME}.jar"                 # 打包输出
HEALTH_URL="http://localhost:8080/actuator/health"
KEEP_BACKUPS=5                             # 保留的备份数量
LOG_FILE="/var/log/deploy-${APP_NAME}.log" # 部署日志

# ---------- 颜色输出 ----------
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "${GREEN}[$(date '+%F %T')]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
err()  { echo -e "${RED}[ERROR]${NC} $*"; }

# ---------- 步骤 1: 拉取代码 ----------
fetch_code() {
    log ">> [1/6] 拉取代码: ${GIT_BRANCH} 分支"
    if [ ! -d "$WORKSPACE/.git" ]; then
        git clone -b "$GIT_BRANCH" "$GIT_REPO" "$WORKSPACE"
    else
        cd "$WORKSPACE"
        git fetch origin
        git checkout "$GIT_BRANCH"
        git reset --hard "origin/$GIT_BRANCH"
        git pull
    fi
}

# ---------- 步骤 2: Maven 构建 ----------
build_app() {
    log ">> [2/6] Maven 打包（跳过测试）"
    cd "$WORKSPACE"
    mvn clean package -DskipTests -q
    # 校验产物存在
    local jar_path=$(find "$WORKSPACE/target" -maxdepth 1 -name "*.jar" ! -name "*sources*" | head -1)
    if [ -z "$jar_path" ]; then
        err "未找到构建产物 jar"
        exit 1
    fi
    JAR_PATH="$jar_path"
    log "构建产物: $JAR_PATH"
}

# ---------- 步骤 3: 备份旧版本 ----------
backup_old() {
    log ">> [3/6] 备份旧版本"
    [ -f "$DEPLOY_DIR/$JAR_NAME" ] || { warn "无旧版本可备份"; return 0; }

    mkdir -p "$BACKUP_DIR"
    local stamp=$(date '+%Y%m%d%H%M%S')
    cp "$DEPLOY_DIR/$JAR_NAME" "$BACKUP_DIR/${JAR_NAME%.jar}-${stamp}.jar"
    log "已备份到: $BACKUP_DIR/${JAR_NAME%.jar}-${stamp}.jar"

    # 只保留最近 KEEP_BACKUPS 份
    ls -1t "$BACKUP_DIR"/*.jar 2>/dev/null | tail -n +$((KEEP_BACKUPS + 1)) | xargs -r rm -f
}

# ---------- 步骤 4: 停止旧进程并部署 ----------
deploy_app() {
    log ">> [4/6] 部署新版本"
    # 停止旧进程（优雅停机）
    local old_pid=$(pgrep -f "java -jar ${DEPLOY_DIR}/${JAR_NAME}" || true)
    if [ -n "$old_pid" ]; then
        kill "$old_pid" 2>/dev/null || true
        # 最多等待 30 秒优雅退出
        for i in $(seq 1 15); do
            pgrep -f "java -jar ${DEPLOY_DIR}/${JAR_NAME}" >/dev/null || break
            sleep 2
        done
        # 强制终止仍存活的
        pkill -9 -f "java -jar ${DEPLOY_DIR}/${JAR_NAME}" 2>/dev/null || true
        log "旧进程已停止"
    fi

    mkdir -p "$DEPLOY_DIR"
    cp "$JAR_PATH" "$DEPLOY_DIR/$JAR_NAME"

    # 启动新版本（nohup 后台运行，记录 PID）
    cd "$DEPLOY_DIR"
    nohup java -Xms256m -Xmx512m -jar "$JAR_NAME" \
        --spring.profiles.active=prod \
        >> "$LOG_FILE" 2>&1 &
    NEW_PID=$!
    echo $NEW_PID > "${DEPLOY_DIR}/app.pid"
    log "新进程已启动 PID=$NEW_PID"
}

# ---------- 步骤 5: 健康检查 ----------
health_check() {
    log ">> [5/6] 健康检查: $HEALTH_URL"
    local retries=0
    local max_retries=30
    while [ $retries -lt $max_retries ]; do
        # 进程存活 + HTTP 健康检查双验证
        if kill -0 "$NEW_PID" 2>/dev/null \
           && curl -sf "$HEALTH_URL" >/dev/null 2>&1; then
            log "✓ 健康检查通过（重试 ${retries} 次）"
            return 0
        fi
        retries=$((retries + 1))
        sleep 2
    done
    err "健康检查失败（${max_retries} 次重试后仍然不可用）"
    return 1
}

# ---------- 步骤 6: 失败回滚 ----------
rollback() {
    log ">> [6/6] 回滚到上一个可用版本"
    local latest_backup=$(ls -1t "$BACKUP_DIR"/*.jar 2>/dev/null | head -1)
    if [ -z "$latest_backup" ]; then
        err "无可用备份，回滚失败！"
        exit 1
    fi

    # 停止故障版本
    kill -9 "$NEW_PID" 2>/dev/null || true
    pkill -9 -f "java -jar ${DEPLOY_DIR}/${JAR_NAME}" 2>/dev/null || true

    # 恢复备份
    cp "$latest_backup" "$DEPLOY_DIR/$JAR_NAME"
    cd "$DEPLOY_DIR"
    nohup java -Xms256m -Xmx512m -jar "$JAR_NAME" \
        --spring.profiles.active=prod >> "$LOG_FILE" 2>&1 &
    log "已回滚到: $(basename "$latest_backup")"
    log "回滚进程 PID=$!"
}

# ---------- 主流程 ----------
main() {
    log "========== 部署开始: ${APP_NAME} =========="
    fetch_code
    build_app
    backup_old
    deploy_app
    if ! health_check; then
        warn "部署失败，执行回滚"
        rollback
        # 回滚后再次检查
        sleep 20
        if curl -sf "$HEALTH_URL" >/dev/null 2>&1; then
            log "✓ 回滚成功，应用已恢复"
        else
            err "回滚后仍未恢复，请人工介入！"
            exit 1
        fi
    fi
    log "========== 部署完成 =========="
}

main