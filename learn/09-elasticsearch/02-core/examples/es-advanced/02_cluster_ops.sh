#!/bin/bash
# ============================================
# Elasticsearch 集群运维操作
# 演示：集群健康 / 节点信息 / 分片分配 / 快照
# 前提：ES 单节点已启动（docker-compose up -d）
# ============================================

ES_URL="http://localhost:9200"

echo "===== 1. 集群健康检查 ====="
echo "集群健康:"
curl -s "$ES_URL/_cluster/health?pretty" | python3 -m json.tool
echo ""

echo "===== 2. 节点信息 ====="
echo "节点列表:"
curl -s "$ES_URL/_cat/nodes?v" | head -10
echo ""

echo "详细节点信息:"
curl -s "$ES_URL/_nodes/stats?pretty" | python3 -c "
import json, sys
data = json.load(sys.stdin)
for node_id, node in data['nodes'].items():
    print(f\"节点: {node['name']}\")
    print(f\"  版本: {node['version']}\")
    print(f\"  角色: {node.get('roles', [])}\")
    print(f\"  CPU: {node['os']['cpu']['percent']}%\")
    print(f\"  内存: {node['jvm']['mem']['heap_used_percent']}%\")
    print(f\"  磁盘: {node['fs']['total']['total_in_bytes'] / 1024**3:.1f}GB\n\")
" 2>/dev/null || curl -s "$ES_URL/_nodes/stats?pretty" | head -50
echo ""

echo "===== 3. 索引信息 ====="
echo "所有索引:"
curl -s "$ES_URL/_cat/indices?v" | head -20
echo ""

echo "===== 4. 分片分配 ====="
echo "分片分配情况:"
curl -s "$ES_URL/_cat/shards?v" | head -20
echo ""

echo "未分配的分片原因:"
curl -s "$ES_URL/_cluster/allocation/explain?pretty" | python3 -m json.tool 2>/dev/null || echo "（无未分配分片）"
echo ""

echo "===== 5. 集群设置 ====="
echo "集群设置（动态可配置）:"
curl -s "$ES_URL/_cluster/settings?include_defaults=true&pretty" | python3 -c "
import json, sys
data = json.load(sys.stdin)
# 只显示关键设置
for key in ['transient', 'persistent']:
    if key in data:
        d = data[key]
        if 'indices' in d:
            print(f'  indices: {json.dumps(d[\"indices\"], indent=4)}')
        if 'cluster' in d:
            print(f'  cluster: {json.dumps(d[\"cluster\"], indent=4)}')
" 2>/dev/null || echo "（设置信息较多，略）"
echo ""

echo "===== 6. 分片分配过滤 ====="
echo "设置分片分配规则（排除特定节点）:"
# curl -s -X PUT "$ES_URL/_cluster/settings" -H 'Content-Type: application/json' -d '{
#   "transient": {
#     "cluster.routing.allocation.exclude._ip": "192.168.1.100"
#   }
# }'
echo "（注释中的示例，实际执行需取消注释）"
echo ""

echo "===== 7. 快照与恢复 ====="
echo "注册快照仓库（文件系统）:"
# 先创建快照目录
# 然后在 ES 配置中添加 path.repo: /snapshots
# curl -s -X PUT "$ES_URL/_snapshot/my_backup" -H 'Content-Type: application/json' -d '{
#   "type": "fs",
#   "settings": {
#     "location": "/snapshots",
#     "compress": true
#   }
# }'
echo "创建快照:"
# curl -s -X PUT "$ES_URL/_snapshot/my_backup/snapshot_20260822?wait_for_completion=true"
echo "查看快照:"
# curl -s "$ES_URL/_snapshot/my_backup/_all?pretty"
echo "恢复快照:"
# curl -s -X POST "$ES_URL/_snapshot/my_backup/snapshot_20260822/_restore" -H 'Content-Type: application/json' -d '{
#   "indices": "products",
#   "rename_pattern": "(.+)",
#   "rename_replacement": "restored_$1"
# }'
echo ""

echo "===== 8. 滚动重启 ====="
echo "ES 滚动重启步骤（支持零停机）:"
echo "  1. 禁止分片分配："
echo "     PUT _cluster/settings"
echo "     { \"transient\": { \"cluster.routing.allocation.enable\": \"none\" } }"
echo "  2. 停止节点、升级、重启"
echo "  3. 重新启用分片分配："
echo "     PUT _cluster/settings"
echo "     { \"transient\": { \"cluster.routing.allocation.enable\": \"all\" } }"
echo "  4. 等待集群恢复："
echo "     GET _cluster/health?wait_for_status=green&timeout=60s"
echo ""

echo "===== 9. 性能监控 ====="
echo "热点线程（排查慢查询）:"
curl -s "$ES_URL/_nodes/hot_threads" | head -30
echo ""

echo "===== 演示完成 ====="