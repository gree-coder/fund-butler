#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
基金大模型分析脚本
调用百炼 qwen3.5-plus 生成盘面分析、风险提示、操作建议
"""

import sys
import json
import requests

API_KEY = "sk-b5a84795d5544bffb9de0971baa134e0"
API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"

def call_llm(prompt, max_tokens=2000):
    """调用大模型 API"""
    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json"
    }
    
    payload = {
        "model": "qwen3.5-plus",
        "messages": [
            {
                "role": "system",
                "content": "你是一位资深基金经理，管理规模 100 亿+，擅长趋势判断和风险控制。请用专业但易懂的语言，给出具体可操作的建议。"
            },
            {
                "role": "user",
                "content": prompt
            }
        ],
        "max_tokens": max_tokens
    }
    
    try:
        response = requests.post(API_URL, headers=headers, json=payload, timeout=30)
        response.raise_for_status()
        result = response.json()
        
        if "choices" in result and len(result["choices"]) > 0:
            content = result["choices"][0]["message"]["content"]
            return content
        else:
            print(f"错误：API 返回格式异常", file=sys.stderr)
            print(json.dumps(result, indent=2), file=sys.stderr)
            return None
            
    except requests.exceptions.RequestException as e:
        print(f"错误：网络请求失败 - {e}", file=sys.stderr)
        return None
    except json.JSONDecodeError as e:
        print(f"错误：JSON 解析失败 - {e}", file=sys.stderr)
        return None

def main():
    if len(sys.argv) < 2:
        print("用法：python3 llm_analysis.py <分析类型> [数据文件]")
        print("分析类型：market(盘面分析) | risk(风险提示) | strategy(操作建议)")
        sys.exit(1)
    
    analysis_type = sys.argv[1]
    data_file = sys.argv[2] if len(sys.argv) > 2 else None
    
    prompts = {
        "market": """你是一位资深基金经理，管理规模 100 亿+，擅长市场分析。用专业但易懂的语言，200 字左右。

大盘数据：
- 上证 4026 点 +0.95%，成交 9984 亿
- 深证 14640 点 +1.61%，成交 13853 亿
- 创业板 3559 点 +2.36%，成交 2722 亿
- 沪深 300 4701 点 +1.19%，成交 6057 亿
- 两市总成交 2.38 万亿，较昨日放量 2334 亿

领涨板块：钴 +0.13%、国际工程 +0.06%、航天装备 +0.05%
领跌板块：氨纶 -0.03%、油田服务 -0.03%
全市场 3700+ 股上涨

请分析：
1) 指数分化情况
2) 成交量变化含义
3) 板块轮动特征
4) 市场情绪判断""",
        
        "risk": """你是一位资深基金经理，擅长风险控制。用专业但易懂的语言，300 字左右。

持仓数据：
- 混合基金 5 只，占比 37.94%
- QDII 6 只，占比 32.67%
- 股票基金 3 只，占比 29.39%

板块分布：
- 科技 32.56%、医药 31.94%、消费 26.79%
- 制造 3.49%、其他 1.84%

健康度 70 分，风险评分 40 分

风险列表：
1) 缺乏低风险资产（0 只债券/货币基金）
2) 整体亏损 4.77%，12 只基金亏损
3) 1 只基金亏损超 15%（中银港股通互联网 -19.68%）

请生成详细风险分析，包含：
1) 风险等级评估
2) 具体影响说明
3) 改进建议""",
        
        "strategy": """你是一位资深基金经理，管理规模 100 亿+，擅长趋势判断和风险控制。请基于以下数据给出专业的市场研判和操作建议。

【大盘数据】
- 创业板 +2.36% 领涨，8 大指数全线上涨
- 成交量 2.38 万亿，较昨日放量 2334 亿
- 板块：科技 +0.02%、医药 +0.02%、消费 +0.02%

【持仓数据】
- 14 只基金，12 只上涨
- 南方香港成长近 10 日 +8.62% 最强
- 中银港股通互联网近 10 日 -1.86% 最弱

【今日要闻】
1. 央行降准 5000 亿
2. AI 产业政策落地
3. 港股科技股反弹

请输出：
1. 宏观面（2-3 句，结合成交量/政策/情绪）
2. 板块热度（科技/消费/医药/金融等，对持仓的直接影响）
3. 趋势判断表格（14 只基金，按近 10 日表现分上升期/震荡/回调）
4. 操作建议四档（每只基金必须归入一档：✅持有/⚠️观望/🔴减仓/🟢加仓，理由引用具体数据如近 10 日涨跌幅、成交量变化等）"""
    }
    
    if analysis_type not in prompts:
        print(f"错误：未知的分析类型 {analysis_type}", file=sys.stderr)
        print("支持的类型：market | risk | strategy", file=sys.stderr)
        sys.exit(1)
    
    prompt = prompts[analysis_type]
    
    # 如果提供了数据文件，读取并追加到 prompt
    if data_file:
        try:
            with open(data_file, 'r', encoding='utf-8') as f:
                data = f.read()
                prompt += f"\n\n【详细数据】\n{data}"
        except Exception as e:
            print(f"警告：无法读取数据文件 {data_file}: {e}", file=sys.stderr)
    
    # 调用大模型
    result = call_llm(prompt)
    
    if result:
        print(result)
    else:
        sys.exit(1)

if __name__ == "__main__":
    main()
