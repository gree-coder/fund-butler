#!/bin/bash
# 基金大模型分析脚本
# 调用百炼 qwen3.5-plus 生成盘面分析、风险提示、操作建议

API_KEY="sk-b5a84795d5544bffb9de0971baa134e0"
API_URL="https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"

# 参数 1: 分析类型 (market/risk/strategy)
TYPE=$1

# 参数 2: 数据文件路径 (JSON)
DATA_FILE=$2

if [ -z "$TYPE" ]; then
    echo "用法：$0 <分析类型> [数据文件]"
    echo "分析类型：market(盘面分析) | risk(风险提示) | strategy(操作建议)"
    exit 1
fi

case $TYPE in
    market)
        # 盘面特征分析
        PROMPT="你是一位资深基金经理，管理规模 100 亿+，擅长市场分析。用专业但易懂的语言，200 字左右。
大盘数据：上证 4026 点+0.95% 成交 9984 亿，深证 14640 点+1.61% 成交 13853 亿，创业板 3559 点+2.36% 成交 2722 亿，沪深 300 4701 点+1.19% 成交 6057 亿。两市总成交 2.38 万亿，较昨日放量 2334 亿。领涨板块：钴 +0.13%、国际工程 +0.06%、航天装备 +0.05%。领跌板块：氨纶 -0.03%、油田服务 -0.03%。全市场 3700+ 股上涨。请分析：1) 指数分化 2) 成交量含义 3) 板块轮动 4) 市场情绪"
        ;;
    risk)
        # 风险分析
        PROMPT="你是一位资深基金经理，擅长风险控制。用专业但易懂的语言，300 字左右。
持仓数据：混合基金 5 只 37.94%，QDII 6 只 32.67%，股票基金 3 只 29.39%。板块分布：科技 32.56%、医药 31.94%、消费 26.79%、制造 3.49%。健康度 70 分，风险评分 40 分。风险列表：1) 缺乏低风险资产（0 只债券/货币基金）2) 整体亏损 4.77%，12 只基金亏损 3) 1 只基金亏损超 15%（中银港股通互联网 -19.68%）。请生成详细风险分析，包含：1) 风险等级 2) 具体影响 3) 改进建议"
        ;;
    strategy)
        # 操作建议（完整数据）
        if [ -z "$DATA_FILE" ]; then
            echo "错误：strategy 类型需要提供数据文件路径"
            exit 1
        fi
        # 从数据文件读取内容
        DATA=$(cat "$DATA_FILE" 2>/dev/null)
        if [ -z "$DATA" ]; then
            echo "错误：无法读取数据文件 $DATA_FILE"
            exit 1
        fi
        PROMPT="你是一位资深基金经理，管理规模 100 亿+，擅长趋势判断和风险控制。请基于以下数据给出专业的市场研判和操作建议：
$DATA
请输出：
1. 宏观面（2-3 句，结合成交量/政策/情绪）
2. 板块热度（科技/消费/医药/金融等，对持仓的直接影响）
3. 趋势判断表格（14 只基金，按近 10 日表现分上升期/震荡/回调）
4. 操作建议四档（每只基金必须归入一档：✅持有/⚠️观望/🔴减仓/🟢加仓，理由引用具体数据如近 10 日涨跌幅、成交量变化等）"
        ;;
    *)
        echo "错误：未知的分析类型 $TYPE"
        echo "支持的类型：market | risk | strategy"
        exit 1
        ;;
esac

# 调用大模型 API
RESPONSE=$(curl -s -X POST "$API_URL" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"model\": \"qwen3.5-plus\",
    \"messages\": [
      {\"role\": \"system\", \"content\": \"你是一位资深基金经理，管理规模 100 亿+，擅长趋势判断和风险控制。请用专业但易懂的语言，给出具体可操作的建议。\"},
      {\"role\": \"user\", \"content\": \"$PROMPT\"}
    ],
    \"max_tokens\": 2000
  }")

# 提取 content 字段
CONTENT=$(echo "$RESPONSE" | jq -r '.choices[0].message.content' 2>/dev/null)

if [ -z "$CONTENT" ] || [ "$CONTENT" = "null" ]; then
    echo "错误：大模型调用失败"
    echo "原始响应：$RESPONSE"
    exit 1
fi

echo "$CONTENT"
