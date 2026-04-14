# 基金管家 MVP (P0) 开发待办事项

> 基于 SPEC.md 拆解的开发任务清单，供审查后进入开发阶段。  
> 任务按依赖关系排列，建议按顺序执行。

---

## 阶段一：项目基础搭建

### Task 1.1 - 后端项目依赖配置
- [x] `pom.xml` 添加依赖：spring-boot-starter-web、mybatis-plus-spring-boot3-starter、mysql-connector-j、caffeine、lombok、jackson、okhttp（用于外部数据请求）
- [x] 配置 `application.yml`：数据源、MyBatis-Plus、缓存、服务端口等
- [x] 添加 `WebConfig.java`：CORS 跨域配置（允许前端开发服务器访问）

### Task 1.2 - 数据库初始化
- [x] 编写 `schema.sql`：创建 fund、fund_nav、account、position、transaction、watchlist 六张表
- [x] 编写 `data.sql`：初始化默认账户数据（支付宝、微信理财通、天天基金等预设账户）
- [x] 配置 Spring Boot 启动时自动执行建表脚本

### Task 1.3 - 前端项目初始化
- [x] 在项目根目录创建 `fund-web/` 前端项目
- [x] 安装核心依赖：antd、echarts、axios、zustand、react-router-dom
- [x] 配置 `vite.config.ts`：API 代理指向后端
- [x] 配置全局样式、Ant Design 主题
- [x] 搭建 `AppLayout` 全局布局组件
- [x] 配置路由

### Task 1.4 - 通用基础代码
- [x] 后端：`Result.java` 统一响应封装、`GlobalExceptionHandler.java` 全局异常处理
- [x] 后端：所有 Entity 类
- [x] 后端：所有 Mapper 接口
- [x] 前端：`api/client.ts` Axios 实例封装
- [x] 前端：`utils/format.ts` 数字格式化
- [x] 前端：`components/PriceChange.tsx` 涨跌幅展示组件

---

## 阶段二：外部数据采集（核心基础设施）

### Task 2.1 - 基金数据源适配器
- [x] 定义 `FundDataSource` 接口：searchFund、getFundDetail、getNavHistory、getEstimateNav
- [x] 实现 `EastMoneyDataSource`：对接天天基金/东方财富公开数据接口
  - 基金搜索（名称/代码模糊匹配）
  - 基金基本信息获取（类型、公司、经理、规模、费率）
  - 历史净值数据获取
  - 十大重仓股和行业分布数据
  - 基金经理信息
- [x] 实现 `StockEstimateDataSource`：股票行情兆底估值
  - 获取重仓股实时行情
  - 基于持仓比例加权计算基金估算涨幅
- [x] 实现 `FundDataAggregator`：多数据源聚合器
  - 主数据源查询 → 失败时降级到备用源
  - 估值数据多源交叉验证
  - 缓存管理（Caffeine本地缓存）
- [x] 实现 `MarketDataSource`：大盘指数实时行情 + 近期K线走势
- [x] 实现 `SectorDataSource`：板块涨跌排行
- [x] 实现 `TiantianFundDataSource`：天天基金详情数据

### Task 2.2 - 数据同步定时任务
- [x] `FundDataSyncScheduler`：定时同步基金净值数据
  - 每交易日 19:30 同步前一日净值
  - 交易时段(9:30-15:00)每5分钟刷新估值缓存
  - 每日 00:30 更新基金基本信息
- [x] 实现交易日判断逻辑（排除周末和法定节假日）

---

## 阶段三：后端业务 API 实现

### Task 3.1 - 基金查询 API
- [x] `FundController` + `FundService`
  - `GET /api/fund/search?keyword=xxx` — 基金模糊搜索（优先查本地库，无结果时查外部源并缓存）
  - `GET /api/fund/{code}` — 基金详情（基本信息 + 实时估值 + 历史业绩）
  - `GET /api/fund/{code}/nav-history?period=1m` — 净值历史数据

### Task 3.2 - 账户管理 API
- [x] `AccountController` + `AccountService`
  - `GET /api/accounts` — 账户列表
  - `POST /api/accounts` — 创建账户
  - `DELETE /api/accounts/{id}` — 删除账户（校验无持仓）

### Task 3.3 - 持仓管理 API
- [x] `PositionController` + `PositionService`
  - `GET /api/positions?accountId=xxx` — 查询持仓列表（含最新净值和估值）
  - `POST /api/positions` — 添加持仓（自动创建买入交易记录）
  - `PUT /api/positions/{id}/transaction` — 加仓/减仓/清仓
  - `DELETE /api/positions/{id}` — 删除持仓
  - `GET /api/positions/{id}/transactions` — 某持仓的交易记录

### Task 3.4 - 自选基金 API
- [x] `WatchlistController` + `WatchlistService`
  - `GET /api/watchlist?group=xxx` — 自选列表（含实时估值和业绩）
  - `POST /api/watchlist` — 添加自选
  - `DELETE /api/watchlist/{id}` — 移除自选
  - `GET /api/watchlist/groups` — 获取分组列表

### Task 3.5 - 首页 Dashboard API
- [x] `DashboardController` + `DashboardService`
  - [x] `GET /api/dashboard` — 聚合数据（总资产、总收益、今日收益、持仓列表含估值）
  - [x] `GET /api/dashboard/profit-trend?days=7` — 收益趋势
  - [x] `GET /api/dashboard/profit-analysis?days=30` — 收益分析（收益曲线+回撤分析）
  - 总资产 = Σ(各持仓份额 × 最新净值)
  - 总收益 = 总资产 - 总成本
  - 今日收益 = Σ(各持仓份额 × 净值 × 估算日涨跌幅)

### Task 3.6 - 收益分析 API
- [x] 收益曲线计算
  - 每日收益金额（当日市值 - 前日市值）
  - 累计收益金额（当日市值 - 总成本）
  - 累计收益率
- [x] 回撤分析计算
  - 最大回撤率、最大回撤金额
  - 回撤区间（开始日期、结束日期、持续天数）
  - 回撤曲线（每日回撤率）
- [x] 绩效指标计算
  - 总收益率、年化收益率
  - 夏普比率（简化版，无风险利率按3%计算）
  - 波动率（日收益标准差年化）
  - 胜率（盈利天数/总交易日）

---

## 阶段四：前端页面实现

### Task 4.1 - 首页 Dashboard
- [x] `AssetOverview` 组件：总资产/总收益/今日收益卡片，支持金额隐藏（眼睛图标切换）
- [x] `PositionList` 组件：持仓基金列表（表格/卡片），展示名称、估值涨跌、持有收益，支持排序
- [x] `ProfitTrend` 组件：ECharts 折线图展示近7/30天收益走势
- [x] 快捷操作入口：添加持仓按钮、搜索基金入口
- [x] 空状态引导：无持仓时展示 `EmptyGuide` 组件

### Task 4.2 - 基金搜索与详情页
- [x] `SearchBar` 组件：全局搜索框，300ms 防抖，实时联想下拉
- [x] `SearchResult` 页：搜索结果列表，支持一键添加自选/持仓
- [x] `FundDetail` 页：
  - 头部：基金名称、代码、类型、实时估值
  - 净值走势 ECharts 图（1月/3月/6月/1年/3年/全部切换）
  - 历史业绩表格（近1周到成立以来）
  - 十大重仓股列表
  - 行业分布饼图
  - 基金经理信息卡片
  - 费率信息
  - 底部操作：添加到自选 / 添加到持仓

### Task 4.3 - 持仓管理页
- [x] `Portfolio` 页：持仓概览
  - 按账户分组的持仓列表
  - 资产分布饼图（按基金类型）
  - 账户筛选 Tab
- [x] `AddPosition` 页：添加持仓表单
  - 基金代码输入+自动联想
  - 选择账户
  - 输入买入金额/份额/净值/日期
  - 表单验证
- [x] `TransactionList` 页：交易记录时间线

### Task 4.4 - 自选基金页
- [x] `Watchlist` 页：
  - 分组 Tab 切换
  - 基金卡片列表（名称、估值、涨跌幅、1月/3月/1年收益率）
  - 添加自选入口
  - 滑动删除/长按删除
  - 排序功能

### Task 4.5 - 收益分析页
- [x] `ProfitAnalysis` 页：收益分析
  - 统计指标卡片（总收益率、年化收益、最大回撤、夏普比率、胜率、波动率）
  - 收益曲线图（累计收益 + 市值曲线，支持30/60/90天切换）
  - 每日收益柱状图（红涨绿跌）
  - 回撤分析图（回撤曲线 + 最大回撤标记）
  - 盈亏统计（盈利天数、亏损天数、总交易日）

---

## 阶段五：联调与完善

### Task 5.1 - 前后端联调
- [x] 全链路测试：基金搜索 → 查看详情 → 添加持仓 → 首页展示
- [ ] 估值数据验证：对比外部平台数据，校准估值算法
- [x] 边界情况处理：空数据、网络错误、外部数据源超时

### Task 5.2 - 体验优化
- [x] 页面加载骨架屏（Ant Design Skeleton）
- [x] 全局 loading 状态管理
- [x] 错误提示统一处理
- [x] 页面底部添加免责声明

### Task 5.3 - 响应式适配
- [ ] 桌面端布局优化（宽屏双栏/三栏布局）
- [ ] 移动端适配（卡片式布局、底部Tab导航）

---

## 阶段六：数据分析能力模块

### Task 6.1 - 数据分析服务层
- [x] `MarketOverviewService`：市场概览（大盘指数 + 板块涨跌 + 近期走势 + 情绪分析）
- [x] `FundDiagnosisService`：基金智能诊断（多维度评分、估值分析、业绩分析）
- [x] `PositionRiskWarningService`：持仓风险预警（组合级风险评估）
- [x] `RebalanceTimingService`：调仓时机提醒

### Task 6.2 - 数据分析数据源
- [x] `MarketDataSource`：8大指数实时行情（A股4+港股2+美股2）+ 新浪K线走势
- [x] `SectorDataSource`：板块涨跌排行 + 近5日/10日涨跌幅
- [x] `TiantianFundDataSource`：天天基金详情数据

### Task 6.3 - 数据分析 CLI 命令
- [x] `ReportCommand`：4个子命令（market/diagnose/risk/positions）
- [x] 剔除主观建议字段，仅输出客观事实性指标
- [x] JSON 格式输出，方便外部 Agent 解析

### Task 6.4 - 数据分析 Web API + 前端
- [x] `ReportController`、`MarketOverviewController`、`PositionRiskController`、`RebalanceTimingController`
- [x] 前端组件：MarketOverviewCard、RiskWarningCard、RebalanceTimingCard、DiagnosisTab

---

## 任务依赖关系

```
阶段一 (基础搭建)
  ├── Task 1.1 后端依赖 ──┐
  ├── Task 1.2 数据库    ──┤── 阶段二 (数据采集)
  ├── Task 1.3 前端初始化 ─┤     ├── Task 2.1 数据源适配器
  └── Task 1.4 通用代码  ──┘     └── Task 2.2 定时任务
                                        │
                                  阶段三 (后端API)
                                    ├── Task 3.1 基金查询 ──┐
                                    ├── Task 3.2 账户管理  ──┤
                                    ├── Task 3.3 持仓管理  ──┼── 阶段四 (前端页面)
                                    ├── Task 3.4 自选基金  ──┤    ├── Task 4.1 Dashboard
                                    └── Task 3.5 Dashboard ──┘    ├── Task 4.2 基金详情
                                                                   ├── Task 4.3 持仓管理
                                                                   └── Task 4.4 自选基金
                                                                         │
                                                                   阶段五 (联调完善)
                                                                     ├── Task 5.1 联调
                                                                     ├── Task 5.2 体验优化
                                                                     └── Task 5.3 响应式
```

---

## 工作量评估

| 阶段 | 任务数 | 复杂度 |
|------|--------|--------|
| 阶段一：项目基础搭建 | 4 | 低 |
| 阶段二：外部数据采集 | 2 | **高**（核心难点） |
| 阶段三：后端业务API | 5 | 中 |
| 阶段四：前端页面 | 5 | 中 |
| 阶段五：联调完善 | 3 | 中 |
| 阶段六：数据分析能力模块 | 4 | 高 |
| **合计** | **23** | |

> **核心难点**：阶段二的外部数据采集是整个项目的基础设施，特别是多数据源验证和股票估值兜底策略，需要投入较多精力确保数据准确性和稳定性。
