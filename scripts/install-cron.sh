#!/bin/bash
#
# 基金管家定时任务安装脚本
# 将 Spring @Scheduled 迁移到系统 crontab
#
# 使用方法: ./install-cron.sh
#

FUND_CLI="/Users/chenzhen/Downloads/fund/target/fund-0.0.1-SNAPSHOT-cli.jar"
LOG_DIR="/Users/chenzhen/Downloads/fund/logs"

# 检查 CLI JAR 是否存在
if [ ! -f "$FUND_CLI" ]; then
    echo "❌ 找不到 CLI JAR: $FUND_CLI"
    echo "请先构建项目: cd /Users/chenzhen/Downloads/fund && ./mvnw clean package -Pcli"
    exit 1
fi

# 确保日志目录存在
mkdir -p "$LOG_DIR"

echo "🪶 基金管家定时任务配置"
echo "========================"
echo ""
echo "交易日任务:"
echo "  14:50 — A股估值快照 (sync estimate)"
echo "  19:30 — 净值同步 (sync nav)"
echo "  20:00 — 预测评估 (sync evaluate) [周一至周五]"
echo "  21:30 — 净值补充 (sync nav)"
echo "  23:00 — QDII估值快照 (sync estimate --qdii)"
echo ""
echo "每日任务:"
echo "  16:00 — 每日播报 (dashboard broadcast --json)"
echo ""

# 备份现有 crontab
echo "📋 备份现有 crontab..."
crontab -l > /tmp/crontab_backup_$(date +%Y%m%d_%H%M%S) 2>/dev/null

# 读取现有 crontab
EXISTING=$(crontab -l 2>/dev/null)

# 清理已有的基金相关 cron（避免重复）
CLEANED=$(echo "$EXISTING" | grep -v "fund.*SNAPSHOT-cli.jar" | grep -v "fund-cli")

# 构建新的 cron 条目
FUND_CRON="
# ===== 基金管家定时任务 (安装于 $(date '+%Y-%m-%d %H:%M')) =====

# 每交易日 14:50 — A股估值快照
50 14 * * 1-5 java -jar $FUND_CLI sync estimate >> $LOG_DIR/cron.log 2>&1

# 每交易日 16:00 — 每日播报
0 16 * * * java -jar $FUND_CLI dashboard broadcast --json >> $LOG_DIR/cron.log 2>&1

# 每交易日 19:30 — 净值同步
30 19 * * 1-5 java -jar $FUND_CLI sync nav >> $LOG_DIR/cron.log 2>&1

# 每交易日 20:00 — 预测评估
0 20 * * 1-5 java -jar $FUND_CLI sync evaluate >> $LOG_DIR/cron.log 2>&1

# 每交易日 21:30 — 净值补充
30 21 * * 1-5 java -jar $FUND_CLI sync nav >> $LOG_DIR/cron.log 2>&1

# 每交易日 23:00 — QDII估值快照
0 23 * * 1-5 java -jar $FUND_CLI sync estimate --qdii >> $LOG_DIR/cron.log 2>&1
# ===== 基金管家定时任务结束 =====
"

# 合并并安装
(echo "$CLEANED" | grep -v "^$"; echo "$FUND_CRON") | crontab -

# 验证
echo ""
echo "✅ 安装完成！当前 crontab:"
echo ""
crontab -l
echo ""
echo "📝 日志输出到: $LOG_DIR/cron.log"
echo "🔧 卸载命令: crontab -r"
echo "📋 查看日志: tail -f $LOG_DIR/cron.log"
