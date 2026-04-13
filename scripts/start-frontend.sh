#!/bin/bash

# 启动前端服务脚本
# 自动清理占用 5173 端口的进程

echo "=== 启动基金管家前端服务 ==="

# 查找并杀死占用 5173 端口的进程
echo "检查端口 5173..."
PID=$(lsof -ti:5173 2>/dev/null)
if [ -n "$PID" ]; then
    echo "发现占用进程 PID: $PID，正在终止..."
    kill -9 $PID 2>/dev/null
    sleep 1
    echo "已清理"
else
    echo "端口 5173 空闲"
fi

# 进入前端目录并启动
cd "$(dirname "$0")/../fund-web" || exit 1

echo "启动 Vite 开发服务器..."
npm run dev
