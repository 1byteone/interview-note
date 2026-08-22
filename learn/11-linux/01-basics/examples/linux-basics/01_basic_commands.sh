#!/bin/bash
# ============================================
# Linux 基础命令演示脚本
# 覆盖：文件操作 / 权限管理 / 进程管理 / 文本处理 / 归档压缩
# 可直接运行：bash 01_basic_commands.sh
# ============================================

set -e  # 出错即停止

echo "==================== 1. 文件操作 ===================="

# 创建演示目录结构
rm -rf /tmp/linux-demo
mkdir -p /tmp/linux-demo/docs /tmp/linux-demo/data

# ls 列出文件（-l 详细信息, -a 包含隐藏文件, -h 人性化大小）
touch /tmp/linux-demo/readme.txt /tmp/linux-demo/data/access.log
echo "ls -lh 显示:"
ls -lh /tmp/linux-demo

# find 查找文件（-name 名称, -type 类型, -mtime 修改时间）
echo "find 查找 .txt 文件:"
find /tmp/linux-demo -name "*.txt"

# cp 复制 / mv 移动重命名
cp /tmp/linux-demo/readme.txt /tmp/linux-demo/docs/readme-backup.txt
mv /tmp/linux-demo/readme.txt /tmp/linux-demo/docs/readme-final.txt
echo "移动后 docs 目录:"
ls /tmp/linux-demo/docs

# rm 删除文件（-r 递归, -f 强制）
rm -f /tmp/linux-demo/docs/readme-backup.txt
echo "已删除备份文件"

echo ""
echo "==================== 2. 权限管理 ===================="

TARGET=/tmp/linux-demo/data/access.log
chmod 644 $TARGET          # rw-r--r--：所有者读写，组和其他只读
chmod u+x $TARGET          # 增加所有者执行权限
chmod 755 /tmp/linux-demo   # 目录 755：可读可执行
ls -l $TARGET

# chown 修改属主属组（需要 root）
# sudo chown root:root /tmp/linux-demo/data/access.log

# 特殊权限位
# chmod 4755 file   -> SUID（以属主身份执行）
# chmod 2755 file   -> SGID（继承属组）
# chmod 1777 /tmp   -> Sticky（仅属主可删）

echo ""
echo "==================== 3. 进程管理 ===================="

# ps 查看进程（aux 显示所有用户进程）
echo "当前 bash 进程:"
ps -ef | grep bash | grep -v grep | head -3

# top 交互式查看（此处演示非交互用法）
top -b -n 1 | head -10

# 后台运行 & 杀进程演示
sleep 100 &
BG_PID=$!
echo "后台任务 PID: $BG_PID"
kill $BG_PID
echo "已 kill 后台任务"
# pkill -f "sleep 100"   # 按命令行模式匹配杀进程

echo ""
echo "==================== 4. 文本处理 ===================="

cat > /tmp/linux-demo/data/employees.txt << 'EOF'
ID,Name,Dept,Salary
1,Alice,Eng,12000
2,Bob,Sales,9000
3,Carol,Eng,15000
4,Dave,Sales,8000
EOF

# grep 模式匹配（-v 排除, -c 计数, -i 忽略大小写）
echo "grep 查找 Eng 部门:"
grep Eng /tmp/linux-demo/data/employees.txt

# sed 流编辑器（s/旧/新/ 替换, -i 直接修改）
echo "sed 将 Sales 替换为 Market:"
sed 's/Sales/Market/' /tmp/linux-demo/data/employees.txt

# awk 按列处理（-F 指定分隔符, $N 列号）
echo "awk 输出姓名和薪资:"
awk -F',' '{print $2, "->", $4}' /tmp/linux-demo/data/employees.txt

# sort 排序 / uniq 去重 / wc 统计
echo "按薪资排序:"
sort -t',' -k4 -rn /tmp/linux-demo/data/employees.txt
wc -l /tmp/linux-demo/data/employees.txt

# tail/head 查看首尾行
echo "最后2行:"
tail -2 /tmp/linux-demo/data/employees.txt

echo ""
echo "==================== 5. 归档压缩 ===================="

# tar 打包 + gzip 压缩
tar -czf /tmp/linux-demo-docs.tar.gz -C /tmp/linux-demo docs data
echo "打包压缩结果:"
ls -lh /tmp/linux-demo-docs.tar.gz

# 解压到新目录
mkdir -p /tmp/linux-demo-restore
tar -xzf /tmp/linux-demo-docs.tar.gz -C /tmp/linux-demo-restore
echo "解压后的文件:"
find /tmp/linux-demo-restore -type f

# 常用压缩命令对比
# tar -czf     gzip 压缩（.tar.gz）
# tar -cjf     bzip2 压缩（.tar.bz2）
# tar -xJf     xz 解压（.tar.xz）
# zip -r a.zip dir / unzip a.zip

echo ""
echo "==================== 演示完成 ===================="
rm -rf /tmp/linux-demo /tmp/linux-demo-restore /tmp/linux-demo-docs.tar.gz