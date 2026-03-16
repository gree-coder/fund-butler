# REST API开发

<cite>
**本文引用的文件**
- [FundController.java](file://src/main/java/com/qoder/fund/controller/FundController.java)
- [AccountController.java](file://src/main/java/com/qoder/fund/controller/AccountController.java)
- [DashboardController.java](file://src/main/java/com/qoder/fund/controller/DashboardController.java)
- [PositionController.java](file://src/main/java/com/qoder/fund/controller/PositionController.java)
- [WatchlistController.java](file://src/main/java/com/qoder/fund/controller/WatchlistController.java)
- [FundDetailDTO.java](file://src/main/java/com/qoder/fund/dto/FundDetailDTO.java)
- [FundSearchDTO.java](file://src/main/java/com/qoder/fund/dto/FundSearchDTO.java)
- [NavHistoryDTO.java](file://src/main/java/com/qoder/fund/dto/NavHistoryDTO.java)
- [PositionDTO.java](file://src/main/java/com/qoder/fund/dto/PositionDTO.java)
- [DashboardDTO.java](file://src/main/java/com/qoder/fund/dto/DashboardDTO.java)
- [AddPositionRequest.java](file://src/main/java/com/qoder/fund/dto/request/AddPositionRequest.java)
- [AddTransactionRequest.java](file://src/main/java/com/qoder/fund/dto/request/AddTransactionRequest.java)
- [AddWatchlistRequest.java](file://src/main/java/com/qoder/fund/dto/request/AddWatchlistRequest.java)
- [Result.java](file://src/main/java/com/qoder/fund/common/Result.java)
</cite>

## 更新摘要
**变更内容**
- 新增完整的API接口文档，涵盖所有控制器的详细接口规范
- 添加统一响应格式说明和错误处理机制
- 补充各控制器的请求参数、响应数据结构和状态码说明
- 完善数据传输对象(DTO)的详细描述

## 目录
1. [简介](#简介)
2. [统一响应格式](#统一响应格式)
3. [FundController接口规范](#fundcontroller接口规范)
4. [AccountController接口规范](#accountcontroller接口规范)
5. [DashboardController接口规范](#dashboardcontroller接口规范)
6. [PositionController接口规范](#positioncontroller接口规范)
7. [WatchlistController接口规范](#watchlistcontroller接口规范)
8. [数据传输对象(DTO)说明](#数据传输对象dto说明)
9. [请求参数验证规则](#请求参数验证规则)
10. [API调用示例](#api调用示例)
11. [错误处理机制](#错误处理机制)
12. [总结](#总结)

## 简介
本指南为基金管理系统提供全面的RESTful API开发指导。系统基于Spring MVC框架，采用统一的响应格式和严格的参数验证机制。本文档详细说明了各个控制器的接口规范、数据传输对象结构、请求参数验证规则以及完整的API调用示例。

## 统一响应格式
系统采用统一的响应格式，所有API接口都返回标准的Result对象结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

**响应字段说明：**
- `code`: HTTP状态码或业务状态码
- `message`: 响应消息描述
- `data`: 实际响应数据

**状态码约定：**
- 200: 成功
- 404: 资源不存在
- 500: 系统错误

**章节来源**
- [Result.java:1-34](file://src/main/java/com/qoder/fund/common/Result.java#L1-L34)

## FundController接口规范

### 基金搜索接口
**接口地址:** `GET /api/fund/search`
**功能描述:** 根据关键词搜索基金信息

**请求参数:**
- `keyword` (string, 必填): 搜索关键词（基金代码或名称）

**响应数据:**
- `list` (array): 基金搜索结果列表，每个元素包含：
  - `code`: 基金代码
  - `name`: 基金名称  
  - `type`: 基金类型

**成功响应示例:**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "code": "164906",
        "name": "易方达消费行业股票",
        "type": "股票型"
      }
    ]
  }
}
```

**章节来源**
- [FundController.java:22-28](file://src/main/java/com/qoder/fund/controller/FundController.java#L22-L28)
- [FundSearchDTO.java:1-11](file://src/main/java/com/qoder/fund/dto/FundSearchDTO.java#L1-L11)

### 基金详情接口
**接口地址:** `GET /api/fund/{code}`
**功能描述:** 获取指定基金的详细信息

**路径参数:**
- `code` (string, 必填): 基金代码

**响应数据:** 基金详细信息，包含：
- `code`: 基金代码
- `name`: 基金名称
- `type`: 基金类型
- `company`: 基金公司
- `manager`: 基金经理
- `establishDate`: 成立日期
- `scale`: 规模
- `riskLevel`: 风险等级
- `feeRate`: 费率信息
- `topHoldings`: 前十大持仓
- `industryDist`: 行业分布
- `latestNav`: 最新净值
- `latestNavDate`: 净值日期
- `estimateNav`: 估算净值
- `estimateReturn`: 估算收益
- `performance`: 历史业绩表现

**成功响应示例:**
```json
{
  "code": 200,
  "message": "success", 
  "data": {
    "code": "164906",
    "name": "易方达消费行业股票",
    "type": "股票型",
    "performance": {
      "week1": 0.02,
      "month1": 0.05,
      "month3": 0.12,
      "year1": 0.35,
      "year3": 0.85
    }
  }
}
```

**章节来源**
- [FundController.java:30-37](file://src/main/java/com/qoder/fund/controller/FundController.java#L30-L37)
- [FundDetailDTO.java:1-39](file://src/main/java/com/qoder/fund/dto/FundDetailDTO.java#L1-L39)

### 基金净值历史接口
**接口地址:** `GET /api/fund/{code}/nav-history`
**功能描述:** 获取基金净值历史数据

**路径参数:**
- `code` (string, 必填): 基金代码

**查询参数:**
- `period` (string, 可选): 时间周期，默认"3m"
  - 支持: "1m", "3m", "6m", "1y", "all"

**响应数据:**
- `dates` (array): 日期数组
- `navs` (array): 对应的净值数组

**成功响应示例:**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "dates": ["2024-01-01", "2024-01-02", "2024-01-03"],
    "navs": [1.2345, 1.2456, 1.2389]
  }
}
```

**章节来源**
- [FundController.java:39-44](file://src/main/java/com/qoder/fund/controller/FundController.java#L39-L44)
- [NavHistoryDTO.java:1-13](file://src/main/java/com/qoder/fund/dto/NavHistoryDTO.java#L1-L13)

## AccountController接口规范

### 账户列表接口
**接口地址:** `GET /api/accounts`
**功能描述:** 获取所有账户列表

**请求参数:** 无

**响应数据:** 账户对象数组，每个账户包含：
- `id`: 账户ID
- `name`: 账户名称
- `platform`: 平台类型

**成功响应示例:**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "支付宝余额宝",
      "platform": "Alipay"
    }
  ]
}
```

**章节来源**
- [AccountController.java:19-22](file://src/main/java/com/qoder/fund/controller/AccountController.java#L19-L22)

### 创建账户接口
**接口地址:** `POST /api/accounts`
**功能描述:** 创建新的账户

**请求体参数:**
- `name` (string, 必填): 账户名称
- `platform` (string, 必填): 平台类型

**响应数据:** 无

**成功响应示例:**
```json
{
  "code": 200,
  "message": "success"
}
```

**章节来源**
- [AccountController.java:24-28](file://src/main/java/com/qoder/fund/controller/AccountController.java#L24-L28)

### 删除账户接口
**接口地址:** `DELETE /api/accounts/{id}`
**功能描述:** 删除指定ID的账户

**路径参数:**
- `id` (integer, 必填): 账户ID

**响应数据:** 无

**成功响应示例:**
```json
{
  "code": 200,
  "message": "success"
}
```

**章节来源**
- [AccountController.java:30-34](file://src/main/java/com/qoder/fund/controller/AccountController.java#L30-L34)

## DashboardController接口规范

### 仪表板概览接口
**接口地址:** `GET /api/dashboard`
**功能描述:** 获取投资组合概览信息

**请求参数:** 无

**响应数据:**
- `totalAsset`: 总资产
- `totalProfit`: 总收益
- `totalProfitRate`: 总收益率
- `todayProfit`: 今日收益
- `positions`: 持仓列表（PositionDTO数组）

**成功响应示例:**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalAsset": 156800.50,
    "totalProfit": 12345.67,
    "totalProfitRate": 0.085,
    "todayProfit": 123.45,
    "positions": []
  }
}
```

**章节来源**
- [DashboardController.java:17-20](file://src/main/java/com/qoder/fund/controller/DashboardController.java#L17-L20)
- [DashboardDTO.java:1-16](file://src/main/java/com/qoder/fund/dto/DashboardDTO.java#L1-L16)

### 收益趋势接口
**接口地址:** `GET /api/dashboard/profit-trend`
**功能描述:** 获取收益趋势数据

**查询参数:**
- `days` (integer, 可选): 天数，默认7天

**响应数据:**
- `dates` (array): 日期数组
- `profits` (array): 对应日期的收益数据

**成功响应示例:**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "dates": ["2024-01-01", "2024-01-02", "2024-01-03"],
    "profits": [100.50, 150.25, 80.75]
  }
}
```

**章节来源**
- [DashboardController.java:22-25](file://src/main/java/com/qoder/fund/controller/DashboardController.java#L22-L25)

## PositionController接口规范

### 持仓列表接口
**接口地址:** `GET /api/positions`
**功能描述:** 获取持仓列表

**查询参数:**
- `accountId` (integer, 可选): 账户ID，用于筛选特定账户的持仓

**响应数据:** PositionDTO对象数组

**成功响应示例:**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "fundCode": "164906",
      "fundName": "易方达消费行业股票",
      "shares": 1000,
      "costAmount": 50000,
      "latestNav": 1.2345,
      "estimateNav": 1.2456,
      "estimateReturn": 123.45,
      "profit": 1234.56,
      "profitRate": 0.025,
      "accountId": 1,
      "accountName": "支付宝余额宝"
    }
  ]
}
```

**章节来源**
- [PositionController.java:22-25](file://src/main/java/com/qoder/fund/controller/PositionController.java#L22-L25)
- [PositionDTO.java:1-24](file://src/main/java/com/qoder/fund/dto/PositionDTO.java#L1-L24)

### 添加持仓接口
**接口地址:** `POST /api/positions`
**功能描述:** 添加新的持仓记录

**请求体参数:**
- `fundCode` (string, 必填): 基金代码
- `accountId` (integer, 可选): 账户ID
- `amount` (number, 必填): 买入金额
- `shares` (number, 必填): 买入份额
- `price` (number, 必填): 成交净值
- `tradeDate` (date, 必填): 交易日期

**响应数据:** 无

**成功响应示例:**
```json
{
  "code": 200,
  "message": "success"
}
```

**章节来源**
- [PositionController.java:27-31](file://src/main/java/com/qoder/fund/controller/PositionController.java#L27-L31)
- [AddPositionRequest.java:1-30](file://src/main/java/com/qoder/fund/dto/request/AddPositionRequest.java#L1-L30)

### 添加交易接口
**接口地址:** `PUT /api/positions/{id}/transaction`
**功能描述:** 为指定持仓添加交易记录

**路径参数:**
- `id` (integer, 必填): 持仓ID

**请求体参数:**
- `type` (string, 必填): 交易类型（买入/卖出）
- `amount` (number, 必填): 交易金额
- `shares` (number, 必填): 交易份额
- `price` (number, 必填): 成交净值
- `fee` (number, 可选): 手续费
- `tradeDate` (date, 必填): 交易日期

**响应数据:** 无

**成功响应示例:**
```json
{
  "code": 200,
  "message": "success"
}
```

**章节来源**
- [PositionController.java:33-39](file://src/main/java/com/qoder/fund/controller/PositionController.java#L33-L39)
- [AddTransactionRequest.java:1-30](file://src/main/java/com/qoder/fund/dto/request/AddTransactionRequest.java#L1-L30)

### 删除持仓接口
**接口地址:** `DELETE /api/positions/{id}`
**功能描述:** 删除指定ID的持仓

**路径参数:**
- `id` (integer, 必填): 持仓ID

**响应数据:** 无

**成功响应示例:**
```json
{
  "code": 200,
  "message": "success"
}
```

**章节来源**
- [PositionController.java:41-45](file://src/main/java/com/qoder/fund/controller/PositionController.java#L41-L45)

### 获取交易记录接口
**接口地址:** `GET /api/positions/{id}/transactions`
**功能描述:** 获取指定持仓的所有交易记录

**路径参数:**
- `id` (integer, 必填): 持仓ID

**响应数据:** 交易记录数组

**成功响应示例:**
```json
{
  "code": 200,
  "message": "success",
  "data": []
}
```

**章节来源**
- [PositionController.java:47-50](file://src/main/java/com/qoder/fund/controller/PositionController.java#L47-L50)

## WatchlistController接口规范

### 自选列表接口
**接口地址:** `GET /api/watchlist`
**功能描述:** 获取自选基金列表

**查询参数:**
- `group` (string, 可选): 分组名称，用于筛选特定分组的自选基金

**响应数据:**
- `list` (array): 自选基金列表
- `groups` (array): 所有分组名称

**成功响应示例:**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [],
    "groups": ["默认", "科技股"]
  }
}
```

**章节来源**
- [WatchlistController.java:19-22](file://src/main/java/com/qoder/fund/controller/WatchlistController.java#L19-L22)

### 添加自选接口
**接口地址:** `POST /api/watchlist`
**功能描述:** 添加基金到自选列表

**请求体参数:**
- `fundCode` (string, 必填): 基金代码
- `groupName` (string, 可选): 分组名称，默认"默认"

**响应数据:** 无

**成功响应示例:**
```json
{
  "code": 200,
  "message": "success"
}
```

**章节来源**
- [WatchlistController.java:24-28](file://src/main/java/com/qoder/fund/controller/WatchlistController.java#L24-L28)
- [AddWatchlistRequest.java:1-14](file://src/main/java/com/qoder/fund/dto/request/AddWatchlistRequest.java#L1-L14)

### 移除自选接口
**接口地址:** `DELETE /api/watchlist/{id}`
**功能描述:** 从自选列表移除指定基金

**路径参数:**
- `id` (integer, 必填): 自选ID

**响应数据:** 无

**成功响应示例:**
```json
{
  "code": 200,
  "message": "success"
}
```

**章节来源**
- [WatchlistController.java:30-34](file://src/main/java/com/qoder/fund/controller/WatchlistController.java#L30-L34)

## 数据传输对象(DTO)说明

### FundDetailDTO - 基金详情
包含完整的基金基本信息和性能数据：
- 基本信息：code, name, type, company, manager, establishDate, scale, riskLevel
- 费用信息：feeRate (Map)
- 持仓信息：topHoldings, industryDist (List)
- 净值信息：latestNav, latestNavDate, estimateNav, estimateReturn
- 历史业绩：PerformanceDTO对象

### FundSearchDTO - 基金搜索结果
简洁的基金搜索结果：
- code: 基金代码
- name: 基金名称  
- type: 基金类型

### NavHistoryDTO - 净值历史
净值历史数据：
- dates: 日期数组
- navs: 对应净值数组

### PositionDTO - 持仓信息
完整的持仓详情：
- 基本信息：id, fundCode, fundName, fundType, accountId, accountName
- 数量信息：shares, costAmount, marketValue
- 估值信息：latestNav, estimateNav, estimateReturn
- 收益信息：profit, profitRate

### DashboardDTO - 仪表板概览
投资组合总体概览：
- 资产统计：totalAsset, totalProfit, totalProfitRate, todayProfit
- 持仓列表：positions (PositionDTO数组)

**章节来源**
- [FundDetailDTO.java:1-39](file://src/main/java/com/qoder/fund/dto/FundDetailDTO.java#L1-L39)
- [FundSearchDTO.java:1-11](file://src/main/java/com/qoder/fund/dto/FundSearchDTO.java#L1-L11)
- [NavHistoryDTO.java:1-13](file://src/main/java/com/qoder/fund/dto/NavHistoryDTO.java#L1-L13)
- [PositionDTO.java:1-24](file://src/main/java/com/qoder/fund/dto/PositionDTO.java#L1-L24)
- [DashboardDTO.java:1-16](file://src/main/java/com/qoder/fund/dto/DashboardDTO.java#L1-L16)

## 请求参数验证规则

### 基础验证规则
系统使用Jakarta Bean Validation进行参数验证，所有必填字段都有明确的约束：

**FundController验证:**
- `keyword` (String): 必填，非空字符串

**PositionController验证:**
- `AddPositionRequest`:
  - `fundCode`: 必填，非空
  - `amount`: 必填，非null
  - `shares`: 必填，非null  
  - `price`: 必填，非null
  - `tradeDate`: 必填，非null

- `AddTransactionRequest`:
  - `type`: 必填，非空
  - `amount`: 必填，非null
  - `shares`: 必填，非null
  - `price`: 必填，非null
  - `tradeDate`: 必填，非null

**WatchlistController验证:**
- `AddWatchlistRequest`:
  - `fundCode`: 必填，非空
  - `groupName`: 可选，默认"默认"

### 错误处理机制
验证失败时返回400状态码和详细的错误信息：
```json
{
  "code": 400,
  "message": "基金代码不能为空",
  "data": null
}
```

**章节来源**
- [AddPositionRequest.java:1-30](file://src/main/java/com/qoder/fund/dto/request/AddPositionRequest.java#L1-L30)
- [AddTransactionRequest.java:1-30](file://src/main/java/com/qoder/fund/dto/request/AddTransactionRequest.java#L1-L30)
- [AddWatchlistRequest.java:1-14](file://src/main/java/com/qoder/fund/dto/request/AddWatchlistRequest.java#L1-L14)

## API调用示例

### 基金搜索示例
```bash
# curl -X GET "http://localhost:8080/api/fund/search?keyword=易方达" \
#   -H "Content-Type: application/json"
```

### 获取基金详情示例
```bash
# curl -X GET "http://localhost:8080/api/fund/164906" \
#   -H "Content-Type: application/json"
```

### 获取持仓列表示例
```bash
# curl -X GET "http://localhost:8080/api/positions?accountId=1" \
#   -H "Content-Type: application/json"
```

### 添加持仓示例
```bash
# curl -X POST "http://localhost:8080/api/positions" \
#   -H "Content-Type: application/json" \
#   -d '{
#     "fundCode": "164906",
#     "accountId": 1,
#     "amount": 10000,
#     "shares": 8100,
#     "price": 1.2345,
#     "tradeDate": "2024-01-01"
#   }'
```

## 错误处理机制

### HTTP状态码映射
- **200**: 成功请求
- **400**: 参数验证失败或业务逻辑错误
- **404**: 请求的资源不存在
- **500**: 服务器内部错误

### 错误响应格式
所有错误响应都遵循统一的Result格式：
```json
{
  "code": 404,
  "message": "基金不存在",
  "data": null
}
```

### 常见错误场景
1. **资源不存在**: 返回404状态码和相应提示
2. **参数缺失**: 返回400状态码和具体缺失字段提示
3. **业务逻辑错误**: 返回400状态码和业务错误描述
4. **系统异常**: 返回500状态码和错误信息

**章节来源**
- [FundController.java:33-36](file://src/main/java/com/qoder/fund/controller/FundController.java#L33-L36)
- [Result.java:26-32](file://src/main/java/com/qoder/fund/common/Result.java#L26-L32)

## 总结
本文档提供了基金管理系统完整的REST API接口规范，涵盖了所有控制器的详细接口说明、数据传输对象结构、参数验证规则和错误处理机制。系统采用统一的响应格式和严格的参数验证，确保了API的一致性和可靠性。开发者可以基于此文档快速理解和使用各个API接口，同时也可以作为系统扩展和维护的重要参考。

通过标准化的接口设计和完善的错误处理机制，该系统能够为前端应用提供稳定可靠的数据服务，支持完整的基金投资管理功能。