#!/bin/bash
# ============================================
# Redis 高级数据类型操作演示
# 启动 Redis 后执行：bash 02_advanced_types.sh
# ============================================

REDIS_CLI="docker exec -i redis-quickstart redis-cli"

echo "===== 1. Bitmap（位图 — 签到/日活统计）====="
# 用户签到：第 1 天签到，第 5 天签到，第 10 天签到
$REDIS_CLI SETBIT user:sign:202608 1 1
$REDIS_CLI SETBIT user:sign:202608 5 1
$REDIS_CLI SETBIT user:sign:202608 10 1
echo "第 1 天签到状态: $($REDIS_CLI GETBIT user:sign:202608 1)"
echo "本月签到总天数: $($REDIS_CLI BITCOUNT user:sign:202608)"
# 统计连续签到（演示：8月第1周）
$REDIS_CLI SETBIT daily:active:20260801 1 1
$REDIS_CLI SETBIT daily:active:20260802 1 1
$REDIS_CLI SETBIT daily:active:20260802 5 1
echo "8月1日活跃用户数: $($REDIS_CLI BITCOUNT daily:active:20260801)"
echo ""

echo "===== 2. HyperLogLog（基数统计 — UV 统计）====="
# 占用固定 12KB 内存，标准误差 0.81%
$REDIS_CLI PFADD page:home:uv "user1" "user2" "user3" "user1" "user4"
$REDIS_CLI PFADD page:home:uv "user5" "user6" "user2"
echo "首页 UV（去重后）: $($REDIS_CLI PFCOUNT page:home:uv)"
# 合并多个 HyperLogLog
$REDIS_CLI PFADD page:detail:uv "user3" "user7" "user8"
$REDIS_CLI PFMERGE page:total:uv page:home:uv page:detail:uv
echo "全站 UV: $($REDIS_CLI PFCOUNT page:total:uv)"
echo ""

echo "===== 3. GEO（地理空间 — 附近的人/店铺）====="
# 添加地理位置（经纬度）
$REDIS_CLI GEOADD shops 116.397 39.908 "天安门店"    # 北京
$REDIS_CLI GEOADD shops 121.473 31.230 "外滩店"       # 上海
$REDIS_CLI GEOADD shops 113.264 23.129 "广州塔店"     # 广州
$REDIS_CLI GEOADD shops 114.057 22.543 "深圳湾店"     # 深圳
# 计算距离
echo "北京到上海距离: $($REDIS_CLI GEODIST shops "天安门店" "外滩店" km)"
# 查找附近店铺（以北京为中心，半径 1200km 内的店铺）
echo "距北京 1200km 内的店铺:"
$REDIS_CLI GEORADIUS shops 116.397 39.908 1200 km WITHDIST
echo ""

echo "===== 4. Stream（流 — 消息队列）====="
# 4.1 生产者：添加消息到 Stream
$REDIS_CLI XADD mystream * event "user_login" username "alice" ip "192.168.1.1"
$REDIS_CLI XADD mystream * event "order_create" order_id "1001" amount "99.00"
$REDIS_CLI XADD mystream * event "payment" order_id "1001" status "success"
echo "Stream 长度: $($REDIS_CLI XLEN mystream)"
echo "Stream 内容:"
$REDIS_CLI XRANGE mystream - + COUNT 5

# 4.2 创建消费者组
$REDIS_CLI XGROUP CREATE mystream mygroup 0 MKSTREAM 2>/dev/null || true
echo "消费者组信息:"
$REDIS_CLI XINFO GROUPS mystream

# 4.3 消费者读取消息
echo "消费者读取未确认消息:"
$REDIS_CLI XREADGROUP GROUP mygroup consumer1 COUNT 2 BLOCK 1000 STREAMS mystream ">"

# 4.4 确认消息
# 获取最后一条消息 ID
LAST_ID=$($REDIS_CLI XRANGE mystream - + COUNT 1 | awk '{print $1}')
$REDIS_CLI XACK mystream mygroup $LAST_ID 2>/dev/null || true
echo ""

echo "===== 清理演示数据 ====="
$REDIS_CLI DEL user:sign:202608 daily:active:20260801 daily:active:20260802
$REDIS_CLI DEL page:home:uv page:detail:uv page:total:uv
$REDIS_CLI DEL shops mystream
echo "演示完成"