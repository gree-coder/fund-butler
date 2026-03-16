# 基金管家 MVP (P0) 开发待办事项

> 基于 SPEC.md 拆解的开发任务清单，供审查后进入开发阶段。  
> 任务按依赖关系排列，建议按顺序执行。

---

## 阶段一：项目基础搭建

### Task 1.1 - 后端项目依赖配置
- [ ] `pom.xml` 添加依赖：spring-boot-starter-web、mybatis-plus-spring-boot3-starter、mysql-connector-j、caffeine、lombok、jackson、okhttp（用于外部数据请求）
- [ ] 配置 `application.yml`：数据源、MyBatis-Plus、缓存、服务端口等
- [ ] 添加 `WebConfig.java`：CORS 跨域配置（允许前端开发服务器访问）

### Task 1.2 - 数据库初始化
- [ ] 编写 `schema.sql`：创建 fund、fund_nav、account、position、transaction、watchlist 六张表
- [ ] 编写 `data.sql`：初始化默认账户数据（支付宝、微信理财通、天天基金等预设账户）
- [ ] 配置 Spring Boot 启动时自动执行建表脚本

### Task 1.3 - 前端项目初始化
- [ ] 在项目根目录创建 `fund-web/` 前端项目：`npm create vite@latest fund-web -- --template react-ts`
- [ ] 安装核心依赖：antd、@ant-design/icons、echarts、echarts-for-react、axios、zustand、react-router-dom
- [ ] 配置 `vite.config.ts`：API 代理指向 `http://localhost:8080`
- [ ] 配置全局样式、Ant Design 主题（品牌蓝 `#1677FF`、红涨绿跌色）
- [ ] 搭建 `AppLayout` 全局布局组件（顶部导航栏 + 全局搜索框 + 侧边/底部导航）
- [ ] 配置路由：Dashboard、SearchResult、FundDetail、Portfolio、AddPosition、TransactionList、Watchlist

### Task 1.4 - 通用基础代码
- [ ] 后端：`Result.java` 统一响应封装、`GlobalExceptionHandler.java` 全局异常处理
- [ ] 后端：所有 Entity 类（Fund、FundNav、Account、Position、Transaction、Watchlist）
- [ ] 后端：所有 Mapper 接口
- [ ] 前端：`api/client.ts` Axios 实例封装（请求拦截、错误提示）
- [ ] 前端：`utils/format.ts` 数字格式化（金额、百分比、涨跌幅带正负号和颜色）
- [ ] 前端：`components/PriceChange.tsx` 涨跌幅展示组件（红涨绿跌）

---

## 阶段二：外部数据采集（核心基础设施）

### Task 2.1 - 基金数据源适配器
- [ ] 定义 `FundDataSource` 接口：searchFund、getFundDetail、getNavHistory、getEstimateNav
- [ ] 实现 `EastMoneyDataSource`：对接天天基金/东方财富公开数据接口
  - 基金搜索（名称/代码模糊匹配）
  - 基金基本信息获取（类型、公司、经理、规模、费率）
  - 历史净值数据获取
  - 十大重仓股和行业分布数据
  - 基金经理信息
- [ ] 实现 `StockEstimateDataSource`：股票行情兜底估值
  - 获取重仓股实时行情
  - 基于持仓比例加权计算基金估算涨幅
- [ ] 实现 `FundDataAggregator`：多数据源聚合器
  - 主数据源查询 → 失败时降级到备用源
  - 估值数据多源交叉验证
  - 缓存管理（Caffeine本地缓存）

### Task 2.2 - 数据同步定时任务
- [ ] `FundDataSyncScheduler`：定时同步基金净值数据
  - 每交易日 19:30 同步前一日净值
  - 交易时段(9:30-15:00)每5分钟刷新估值缓存
  - 每日 00:30 更新基金基本信息
- [ ] 实现交易日判断逻辑（排除周末和法定节假日）

---

## 阶段三：后端业务 API 实现

### Task 3.1 - 基金查询 API
- [ ] `FundController` + `FundService`
  - `GET /api/fund/search?keyword=xxx` — 基金模糊搜索（优先查本地库，无结果时查外部源并缓存）
  - `GET /api/fund/{code}` — 基金详情（基本信息 + 实时估值 + 历史业绩）
  - `GET /api/fund/{code}/nav-history?period=1m` — 净值历史数据

### Task 3.2 - 账户管理 API
- [ ] `AccountController` + `AccountService`
  - `GET /api/accounts` — 账户列表
  - `POST /api/accounts` — 创建账户
  - `DELETE /api/accounts/{id}` — 删除账户（校验无持仓）

### Task 3.3 - 持仓管理 API
- [ ] `PositionController` + `PositionService`
  - `GET /api/positions?accountId=xxx` — 查询持仓列表（含最新净值和估值）
  - `POST /api/positions` — 添加持仓（自动创建买入交易记录）
  - `PUT /api/positions/{id}/transaction` — 加仓/减仓/清仓
  - `DELETE /api/positions/{id}` — 删除持仓
  - `GET /api/positions/{id}/transactions` — 某持仓的交易记录

### Task 3.4 - 自选基金 API
- [ ] `WatchlistController` + `WatchlistService`
  - `GET /api/watchlist?group=xxx` — 自选列表（含实时估值和业绩）
  - `POST /api/watchlist` — 添加自选
  - `DELETE /api/watchlist/{id}` — 移除自选
  - `GET /api/watchlist/groups` — 获取分组列表

### Task 3.5 - 首页 Dashboard API
- [ ] `DashboardController` + `DashboardService`
  - `GET /api/dashboard` — 聚合数据（总资产、总收益、今日收益、持仓列表含估值）
  - `GET /api/dashboard/profit-trend?days=7` — 收益趋势
  - 总资产 = Σ(各持仓份额 × 最新净值)
  - 总收益 = 总资产 - 总成本
  - 今日收益 = Σ(各持仓份额 × 净值 × 估算日涨跌幅)

---

## 阶段四：前端页面实现

### Task 4.1 - 首页 Dashboard
- [ ] `AssetOverview` 组件：总资产/总收益/今日收益卡片，支持金额隐藏（眼睛图标切换）
- [ ] `PositionList` 组件：持仓基金列表（表格/卡片），展示名称、估值涨跌、持有收益，支持排序
- [ ] `ProfitTrend` 组件：ECharts 折线图展示近7/30天收益走势
- [ ] 快捷操作入口：添加持仓按钮、搜索基金入口
- [ ] 空状态引导：无持仓时展示 `EmptyGuide` 组件

### Task 4.2 - 基金搜索与详情页
- [ ] `SearchBar` 组件：全局搜索框，300ms 防抖，实时联想下拉
- [ ] `SearchResult` 页：搜索结果列表，支持一键添加自选/持仓
- [ ] `FundDetail` 页：
  - 头部：基金名称、代码、类型、实时估值
  - 净值走势 ECharts 图（1月/3月/6月/1年/3年/全部切换）
  - 历史业绩表格（近1周到成立以来）
  - 十大重仓股列表
  - 行业分布饼图
  - 基金经理信息卡片
  - 费率信息
  - 底部操作：添加到自选 / 添加到持仓

### Task 4.3 - 持仓管理页
- [ ] `Portfolio` 页：持仓概览
  - 按账户分组的持仓列表
  - 资产分布饼图（按基金类型）
  - 账户筛选 Tab
- [ ] `AddPosition` 页：添加持仓表单
  - 基金代码输入+自动联想
  - 选择账户
  - 输入买入金额/份额/净值/日期
  - 表单验证
- [ ] `TransactionList` 页：交易记录时间线

### Task 4.4 - 自选基金页
- [ ] `Watchlist` 页：
  - 分组 Tab 切换
  - 基金卡片列表（名称、估值、涨跌幅、1月/3月/1年收益率）
  - 添加自选入口
  - 滑动删除/长按删除
  - 排序功能

---

## 阶段五：联调与完善

### Task 5.1 - 前后端联调
- [ ] 全链路测试：基金搜索 → 查看详情 → 添加持仓 → 首页展示
- [ ] 估值数据验证：对比外部平台数据，校准估值算法
- [ ] 边界情况处理：空数据、网络错误、外部数据源超时

### Task 5.2 - 体验优化
- [ ] 页面加载骨架屏（Ant Design Skeleton）
- [ ] 全局 loading 状态管理
- [ ] 错误提示统一处理（网络错误、数据源不可用降级提示）
- [ ] 页面底部添加免责声明："数据仅供参考，不构成投资建议"

### Task 5.3 - 响应式适配
- [ ] 桌面端布局优化（宽屏双栏/三栏布局）
- [ ] 移动端适配（卡片式布局、底部Tab导航）

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
| 阶段四：前端页面 | 4 | 中 |
| 阶段五：联调完善 | 3 | 中 |
| **合计** | **18** | |

> **核心难点**：阶段二的外部数据采集是整个项目的基础设施，特别是多数据源验证和股票估值兜底策略，需要投入较多精力确保数据准确性和稳定性。
