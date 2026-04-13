# 基金管家 MVP (P0) 技术规格说明书

> 版本：v1.0  
> 日期：2026-03-16  
> 基于：PRD v1.0 P0 需求

---

## 一、技术决策总览

| 决策项 | 选型 | 说明 |
|--------|------|------|
| 前端框架 | React 18 + TypeScript + Vite | 组件化开发，类型安全 |
| UI 组件库 | Ant Design 5 | 企业级组件，金融数据展示友好 |
| 图表库 | ECharts 5 | 金融图表支持完善，交互能力强 |
| 状态管理 | Zustand | 轻量级，API简洁 |
| HTTP 客户端 | Axios | 请求拦截、错误处理 |
| 后端框架 | Spring Boot 3.4.3 (Java 17) | 基于现有项目 |
| ORM | MyBatis-Plus | 简化 CRUD，代码生成 |
| 数据库 | MySQL 8.0 | 关系型数据存储 |
| 缓存 | Caffeine (本地缓存) | MVP阶段无需Redis，本地缓存即可 |
| API风格 | RESTful JSON | 前后端分离 |
| 认证 | 暂无（单用户模式） | MVP阶段简化，后续接入 |

---

## 二、系统架构

```
┌─────────────────────────────────────────────┐
│                  Browser                     │
│   React + TypeScript + Ant Design + ECharts │
└──────────────────┬──────────────────────────┘
                   │ HTTP REST (JSON)
┌──────────────────▼──────────────────────────┐
│             Spring Boot 后端                 │
│  ┌──────────┬──────────┬──────────────────┐ │
│  │Controller│ Service  │ Data Fetcher     │ │
│  │  (API)   │ (业务)   │ (外部数据采集)    │ │
│  └────┬─────┴────┬─────┴───────┬──────────┘ │
│       │          │             │             │
│  ┌────▼─────┐ ┌──▼───┐  ┌─────▼──────────┐ │
│  │ MyBatis  │ │Cache │  │ 多数据源适配器  │ │
│  │  Plus    │ │本地  │  │ (天天基金/东财/ │ │
│  │          │ │      │  │  股票估值兜底)  │ │
│  └────┬─────┘ └──────┘  └─────┬──────────┘ │
└───────┼───────────────────────┼─────────────┘
   ┌────▼─────┐          ┌──────▼──────┐
   │  MySQL   │          │ 外部API/网页 │
   │  8.0     │          │ 数据源      │
   └──────────┘          └─────────────┘
```

---

## 三、项目结构

### 3.1 后端目录结构

```
src/main/java/com/qoder/fund/
├── FundApplication.java              # 启动类
├── config/
│   ├── WebConfig.java                # CORS、序列化等配置
│   ├── CacheConfig.java             # 缓存配置
│   └── SchedulerConfig.java         # 定时任务配置
├── controller/
│   ├── FundController.java          # 基金查询 API
│   ├── PositionController.java      # 持仓管理 API
│   ├── AccountController.java       # 账户管理 API
│   ├── WatchlistController.java     # 自选基金 API
│   └── DashboardController.java     # 首页聚合 API
├── service/
│   ├── FundService.java             # 基金数据业务逻辑
│   ├── PositionService.java         # 持仓管理业务
│   ├── AccountService.java          # 账户管理业务
│   ├── WatchlistService.java        # 自选基金业务
│   ├── DashboardService.java        # 首页数据聚合
│   └── FundDataFetchService.java    # 外部数据采集
├── mapper/
│   ├── FundMapper.java
│   ├── FundNavMapper.java
│   ├── PositionMapper.java
│   ├── TransactionMapper.java
│   ├── AccountMapper.java
│   └── WatchlistMapper.java
├── entity/
│   ├── Fund.java                    # 基金基本信息
│   ├── FundNav.java                 # 基金净值
│   ├── Position.java                # 持仓
│   ├── Transaction.java             # 交易记录
│   ├── Account.java                 # 账户
│   └── Watchlist.java               # 自选基金
├── dto/
│   ├── FundDetailDTO.java           # 基金详情响应
│   ├── FundSearchDTO.java           # 搜索结果
│   ├── PositionDTO.java             # 持仓响应
│   ├── DashboardDTO.java            # 首页聚合响应
│   └── request/                     # 请求体
│       ├── AddPositionRequest.java
│       ├── AddTransactionRequest.java
│       └── AddWatchlistRequest.java
├── datasource/
│   ├── FundDataSource.java          # 数据源接口
│   ├── EastMoneyDataSource.java     # 东方财富/天天基金
│   ├── StockEstimateDataSource.java # 股票估值兜底
│   └── FundDataAggregator.java      # 多数据源聚合器
├── scheduler/
│   └── FundDataSyncScheduler.java   # 净值数据定时同步
└── common/
    ├── Result.java                  # 统一响应封装
    └── GlobalExceptionHandler.java  # 全局异常处理
```

### 3.2 前端目录结构

```
fund-web/
├── index.html
├── vite.config.ts
├── tsconfig.json
├── package.json
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── routes.tsx                   # 路由配置
│   ├── api/                         # API 请求层
│   │   ├── client.ts               # Axios 实例
│   │   ├── fund.ts                 # 基金相关 API
│   │   ├── position.ts             # 持仓相关 API
│   │   ├── watchlist.ts            # 自选基金 API
│   │   └── dashboard.ts            # 首页 API
│   ├── stores/                      # Zustand 状态
│   │   ├── usePositionStore.ts
│   │   └── useWatchlistStore.ts
│   ├── pages/
│   │   ├── Dashboard/              # 首页
│   │   │   ├── index.tsx
│   │   │   ├── AssetOverview.tsx   # 资产总览卡片
│   │   │   ├── PositionList.tsx    # 持仓列表
│   │   │   └── ProfitTrend.tsx     # 收益趋势图
│   │   ├── Fund/                   # 基金查询
│   │   │   ├── SearchResult.tsx    # 搜索结果
│   │   │   └── FundDetail.tsx      # 基金详情
│   │   ├── Portfolio/              # 我的持仓
│   │   │   ├── index.tsx           # 持仓概览
│   │   │   ├── AddPosition.tsx     # 添加持仓
│   │   │   └── TransactionList.tsx # 交易记录
│   │   └── Watchlist/              # 自选基金
│   │       └── index.tsx
│   ├── components/                  # 通用组件
│   │   ├── AppLayout.tsx           # 全局布局
│   │   ├── SearchBar.tsx           # 全局搜索框
│   │   ├── FundCard.tsx            # 基金卡片
│   │   ├── PriceChange.tsx         # 涨跌幅显示
│   │   └── EmptyGuide.tsx          # 空状态引导
│   ├── hooks/
│   │   └── useAmountVisible.ts     # 金额显隐 hook
│   └── utils/
│       ├── format.ts               # 数字/日期格式化
│       └── constants.ts            # 常量
```

---

## 四、数据库设计

### 4.1 ER 图关系

```
Account 1──N Position N──1 Fund
                │
                │ 1
                │
                N
          Transaction

Watchlist N──1 Fund
```

### 4.2 表结构

#### fund (基金基本信息)

```sql
CREATE TABLE fund (
    code         VARCHAR(10)  PRIMARY KEY COMMENT '基金代码',
    name         VARCHAR(100) NOT NULL COMMENT '基金名称',
    type         VARCHAR(20)  COMMENT '基金类型: STOCK/MIXED/BOND/MONEY/QDII/INDEX',
    company      VARCHAR(100) COMMENT '基金公司',
    manager      VARCHAR(50)  COMMENT '基金经理',
    establish_date DATE       COMMENT '成立日期',
    scale        DECIMAL(14,2) COMMENT '基金规模(亿)',
    risk_level   TINYINT      COMMENT '风险等级: 1低/2中低/3中/4中高/5高',
    fee_rate     JSON         COMMENT '费率信息JSON: {buy,sell,manage,custody}',
    top_holdings JSON         COMMENT '十大重仓股JSON',
    industry_dist JSON        COMMENT '行业分布JSON',
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_type (type),
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基金基本信息';
```

#### fund_nav (基金净值)

```sql
CREATE TABLE fund_nav (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    fund_code    VARCHAR(10)  NOT NULL COMMENT '基金代码',
    nav_date     DATE         NOT NULL COMMENT '净值日期',
    nav          DECIMAL(10,4) NOT NULL COMMENT '单位净值',
    acc_nav      DECIMAL(10,4) COMMENT '累计净值',
    daily_return DECIMAL(8,4) COMMENT '日涨跌幅(%)',
    UNIQUE KEY uk_code_date (fund_code, nav_date),
    INDEX idx_fund_code (fund_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基金净值历史';
```

#### account (账户)

```sql
CREATE TABLE account (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(50)  NOT NULL COMMENT '账户名称',
    platform     VARCHAR(50)  COMMENT '平台: alipay/wechat/ttfund/other',
    icon         VARCHAR(20)  COMMENT '图标标识',
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资账户';
```

#### position (持仓)

```sql
CREATE TABLE position (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    account_id   BIGINT       COMMENT '所属账户ID',
    fund_code    VARCHAR(10)  NOT NULL COMMENT '基金代码',
    shares       DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '持有份额',
    cost_amount  DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '持仓成本(元)',
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_fund_code (fund_code),
    INDEX idx_account_id (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基金持仓';
```

#### transaction (交易记录)

```sql
CREATE TABLE transaction (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    position_id  BIGINT       NOT NULL COMMENT '关联持仓ID',
    fund_code    VARCHAR(10)  NOT NULL COMMENT '基金代码',
    type         VARCHAR(10)  NOT NULL COMMENT '交易类型: BUY/SELL/DIVIDEND',
    amount       DECIMAL(14,2) COMMENT '交易金额(元)',
    shares       DECIMAL(14,2) COMMENT '交易份额',
    price        DECIMAL(10,4) COMMENT '成交净值',
    fee          DECIMAL(10,2) DEFAULT 0 COMMENT '手续费',
    trade_date   DATE         NOT NULL COMMENT '交易日期',
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_position_id (position_id),
    INDEX idx_trade_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易记录';
```

#### watchlist (自选基金)

```sql
CREATE TABLE watchlist (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    fund_code    VARCHAR(10)  NOT NULL COMMENT '基金代码',
    group_name   VARCHAR(50)  DEFAULT '默认' COMMENT '分组名称',
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_code (fund_code, group_name),
    INDEX idx_group (group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自选基金';
```

---

## 五、API 接口设计

所有接口统一响应格式：
```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

### 5.1 首页 Dashboard

#### GET /api/dashboard

聚合首页数据，包含资产总览和持仓基金实时估值。

**响应：**
```json
{
  "totalAsset": 150000.00,
  "totalProfit": 12500.00,
  "totalProfitRate": 9.09,
  "todayProfit": 350.00,
  "positions": [
    {
      "fundCode": "110011",
      "fundName": "易方达中小盘混合",
      "fundType": "MIXED",
      "shares": 5000.00,
      "costAmount": 30000.00,
      "latestNav": 6.5432,
      "estimateNav": 6.5800,
      "estimateReturn": 0.56,
      "marketValue": 32716.00,
      "profit": 2716.00,
      "profitRate": 9.05,
      "accountName": "支付宝"
    }
  ]
}
```

#### GET /api/dashboard/profit-trend?days=7

获取收益趋势数据。

**参数：** `days` - 天数（7/30）

**响应：**
```json
{
  "dates": ["2026-03-10", "2026-03-11", ...],
  "profits": [120.50, -80.30, 350.00, ...]
}
```

---

### 5.2 基金查询

#### GET /api/fund/search?keyword=xxx

基金模糊搜索，支持名称和代码。

**响应：**
```json
{
  "list": [
    { "code": "110011", "name": "易方达中小盘混合", "type": "MIXED" }
  ]
}
```

#### GET /api/fund/{code}

基金详情。

**响应：**
```json
{
  "code": "110011",
  "name": "易方达中小盘混合",
  "type": "MIXED",
  "company": "易方达基金",
  "manager": "张坤",
  "establishDate": "2008-06-19",
  "scale": 234.56,
  "riskLevel": 4,
  "feeRate": { "buy": 0.15, "sell": 0.50, "manage": 1.50, "custody": 0.25 },
  "topHoldings": [
    { "stockCode": "600519", "stockName": "贵州茅台", "ratio": 9.8 }
  ],
  "industryDist": [
    { "industry": "食品饮料", "ratio": 25.6 }
  ],
  "latestNav": 6.5432,
  "latestNavDate": "2026-03-14",
  "estimateNav": 6.5800,
  "estimateReturn": 0.56,
  "performance": {
    "week1": 1.2, "month1": 3.5, "month3": 8.2,
    "month6": 12.5, "year1": 25.3, "year3": 45.6, "sinceEstablish": 554.3
  }
}
```

#### GET /api/fund/{code}/nav-history?period=1m

净值历史数据（用于走势图）。

**参数：** `period` - 时段（1m/3m/6m/1y/3y/all）

**响应：**
```json
{
  "dates": ["2026-02-14", "2026-02-17", ...],
  "navs": [6.3210, 6.3500, ...],
  "benchmark": [4520.30, 4535.60, ...]
}
```

---

### 5.3 持仓管理

#### GET /api/positions

查询所有持仓。

**参数：** `accountId`（可选） - 按账户筛选

#### POST /api/positions

添加持仓（同时创建一笔买入交易记录）。

**请求体：**
```json
{
  "fundCode": "110011",
  "accountId": 1,
  "amount": 10000.00,
  "shares": 1528.35,
  "price": 6.5432,
  "tradeDate": "2026-03-10"
}
```

#### PUT /api/positions/{id}/transaction

加仓/减仓/清仓操作。

**请求体：**
```json
{
  "type": "BUY",
  "amount": 5000.00,
  "shares": 764.18,
  "price": 6.5432,
  "fee": 7.50,
  "tradeDate": "2026-03-15"
}
```

#### DELETE /api/positions/{id}

删除持仓及其交易记录。

#### GET /api/positions/{id}/transactions

查询某持仓的交易记录。

---

### 5.4 账户管理

#### GET /api/accounts

查询所有账户。

#### POST /api/accounts

创建账户。

**请求体：**
```json
{ "name": "支付宝", "platform": "alipay" }
```

#### DELETE /api/accounts/{id}

删除账户（需检查账户下无持仓）。

---

### 5.5 自选基金

#### GET /api/watchlist?group=xxx

查询自选基金列表（可按分组筛选）。

**响应：**
```json
{
  "list": [
    {
      "id": 1,
      "fundCode": "110011",
      "fundName": "易方达中小盘混合",
      "groupName": "待买入",
      "latestNav": 6.5432,
      "estimateReturn": 0.56,
      "performance": { "month1": 3.5, "month3": 8.2, "year1": 25.3 }
    }
  ],
  "groups": ["默认", "待买入", "观察中"]
}
```

#### POST /api/watchlist

添加自选。

**请求体：**
```json
{ "fundCode": "110011", "groupName": "待买入" }
```

#### DELETE /api/watchlist/{id}

移除自选。

---

## 5.6 数据分析报告 API

#### GET /api/report/market-overview

市场概览，包含大盘指数、板块涨跌、近期走势、市场情绪分析。

#### GET /api/report/diagnosis/{code}

基金诊断报告，多维度评分（业绩、风险、估值、稳定性、费率），基于规则引擎生成。

#### GET /api/report/risk-warning

持仓风险预警，组合级风险评估（集中度、行业分散、估值健康度）。

#### GET /api/report/rebalance-timing

调仓时机数据，基于历史业绩与实时指标双维度分析。

### CLI 数据分析报告命令

| 命令 | 说明 | 输出 |
|------|------|------|
| `report market` | 市场概览（大盘+板块+近期走势+indexSummary汇总） | JSON（剔除主观建议） |
| `report diagnose <code>` | 单只基金诊断 | JSON（剔除主观建议） |
| `report risk` | 持仓风险分析 | JSON（剔除主观建议） |
| `report positions` | 持仓客观指标（近期走势+重仓股表现+数据源准确度） | JSON |

> **设计原则**：CLI 仅作为数据供给层，输出客观事实性指标，不输出任何主观建议，决策权交给外部 Agent。所有分析基于规则引擎 + 外部 API 数据，不涉及 LLM。

---

### 6.1 多数据源架构

```
FundDataAggregator (聚合器)
├── EastMoneyDataSource (主数据源: 天天基金/东方财富)
│   ├── 基金基本信息
│   ├── 历史净值
│   ├── 基金经理信息
│   └── 持仓数据(季报)
│
├── 实时估值策略（多源验证）
│   ├── 数据源A → 直接获取估值
│   ├── 数据源B → 交叉验证
│   └── StockEstimateDataSource (兆底)
│       ├── 获取基金最新持仓(十大重仓股+比例)
│       ├── 获取各重仓股实时行情
│       └── 加权计算 → 估算基金涨幅
│
├── MarketDataSource (市场数据)
│   ├── 大盘指数实时行情（新浪财经/东方财富）
│   └── 大盘近期K线走势（新浪财经 K线 API）
│
├── SectorDataSource (板块数据)
│   └── 板块涨跌排行（东方财富）
│
└── 数据一致性校验
    └── 多源结果偏差 > 阈值 → 日志告警
```

### 6.2 兜底估值算法

当外部数据源无法直接返回基金实时估值时，通过以下逻辑估算：

```
估算净值 = 昨日净值 × (1 + 估算涨幅)

估算涨幅 = Σ(重仓股i的实时涨幅 × 持仓比例i) / 总持仓比例
```

**注意事项**：
- 仅使用十大重仓股数据（季报公开数据），实际覆盖比例通常在 50%-80%
- 估算结果标注"仅供参考"提示
- 非交易时间使用最新净值，不做估算

### 6.3 数据同步策略

| 数据类型 | 同步频率 | 触发方式 |
|---------|---------|---------|
| 基金基本信息 | 每日一次 | 定时任务 00:30 |
| 历史净值 | 每交易日 19:00 后 | 定时任务 19:30 |
| 实时估值 | 交易时段(9:30-15:00) 每5分钟 | 定时任务 |
| 持仓数据(重仓股) | 季报发布后手动/定时 | 定时任务(每季度末+30天) |
| 股票实时行情(兜底用) | 交易时段按需获取 | 请求触发+缓存5分钟 |

---

## 七、前端关键实现说明

### 7.1 路由设计

```typescript
// MVP 路由
const routes = [
  { path: '/',           element: <Dashboard /> },
  { path: '/search',     element: <SearchResult /> },
  { path: '/fund/:code', element: <FundDetail /> },
  { path: '/portfolio',  element: <Portfolio /> },
  { path: '/portfolio/add', element: <AddPosition /> },
  { path: '/portfolio/records', element: <TransactionList /> },
  { path: '/watchlist',  element: <Watchlist /> },
];
```

### 7.2 关键交互

1. **资产金额隐藏**：通过 `useAmountVisible` hook 管理，状态存 localStorage
2. **持仓排序**：默认按持有金额降序，支持切换按涨跌幅、收益率排序
3. **搜索联想**：输入 >= 2字符后触发搜索，300ms 防抖
4. **空状态**：新用户首页展示引导卡片，引导添加第一只基金
5. **涨跌颜色**：红涨绿跌 (`#F5222D` / `#52C41A`)，符合中国市场习惯
6. **净值走势图**：ECharts 折线图，支持缩放拖动查看具体日期数据

### 7.3 开发服务代理

Vite 开发环境配置 `/api` 代理到 Spring Boot 后端 `http://localhost:8080`。

---

## 八、关键约束与注意事项

1. **合规提示**：所有估值数据页面底部展示 "数据仅供参考，不构成投资建议" 免责声明
2. **数据缓存**：基金基本信息缓存24小时，净值数据缓存至下一交易日，估值数据缓存5分钟
3. **错误处理**：外部数据源不可用时，降级展示缓存数据并提示 "数据可能有延迟"
4. **单用户模式**：MVP阶段所有数据表无 `user_id` 字段，后续扩展时添加
5. **前后端分离**：前端构建产物可独立部署，也可打包到 Spring Boot 的 `static` 目录
