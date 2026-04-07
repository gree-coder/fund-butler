# 基金管家

<p align="center">
  <img src="fund-web/public/favicon.svg" width="80" alt="基金管家 Logo">
</p>

<p align="center">
  <strong>一站式基金数据聚合管理工具</strong>
</p>

<p align="center">
  <a href="#功能特性">功能特性</a> •
  <a href="#技术栈">技术栈</a> •
  <a href="#快速开始">快速开始</a> •
  <a href="#项目结构">项目结构</a> •
  <a href="#API文档">API文档</a>
</p>

---

## 简介

**基金管家**是一款面向个人投资者的基金管理与查询 Web 应用，定位为"一站式基金数据聚合管理工具"。产品不涉及真实交易，专注于**基金数据展示、持仓管理、收益分析和投资决策辅助**，帮助用户高效管理分散在多个平台的基金投资。

### 核心特性

- **纯工具属性**：不做交易，不接触用户资金，零风险使用
- **Web 优先**：无需下载 App，浏览器直接使用，跨设备同步
- **CLI 支持**：命令行工具，支持定时任务和数据同步
- **数据聚合**：汇总多平台持仓，一屏掌握投资全貌
- **智能分析**：提供专业级收益归因、风险分析和资产配置建议
- **实时估值**：多数据源实时估值，交易日盘中动态更新

---

## 功能特性

### 已实现功能

- [x] **首页 Dashboard** - 资产总览、今日收益、持仓列表、收益趋势
- [x] **基金查询** - 支持基金名称/代码搜索，实时联想
- [x] **基金详情** - 净值走势、历史业绩、基金经理、持仓分析
- [x] **持仓管理** - 手动添加/编辑持仓，交易记录管理
- [x] **自选基金** - 关注基金列表，分组管理
- [x] **多账户管理** - 支持创建多个账户（支付宝、天天基金等）
- [x] **实时估值** - 多数据源估值聚合，智能权重算法
- [x] **资产配置** - 按行业分布展示资产配置饼图
- [x] **收益分析** - 收益曲线、回撤分析、夏普比率、胜率统计
- [x] **CLI 工具** - 命令行接口，支持定时任务和数据同步

### 进行中功能

- [ ] 基金排行榜与筛选器
- [ ] 指数估值表
- [ ] 定投计算器

---

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.4.3 | 核心框架 |
| Java | 17 | 编程语言 |
| MyBatis-Plus | 3.5.9 | ORM 框架 |
| MySQL | 8.x | 数据库 |
| Caffeine | - | 本地缓存 |
| OkHttp | 4.12.0 | HTTP 客户端 |
| Lombok | - | 代码简化 |
| Picocli | 4.7.5 | CLI 框架 |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| React | 19.2.4 | UI 框架 |
| TypeScript | 5.9.3 | 类型系统 |
| Vite | 8.0.0 | 构建工具 |
| Ant Design | 6.3.3 | UI 组件库 |
| ECharts | 6.0.0 | 图表库 |
| Zustand | 5.0.12 | 状态管理 |
| React Query | 5.96.1 | 数据获取 |

---

## 快速开始

### 环境要求

- JDK 17+
- Node.js 18+
- MySQL 8.0+
- Maven 3.8+

### 后端启动

```bash
# 1. 克隆项目
git clone <repository-url>
cd fund

# 2. 配置数据库
# 修改 src/main/resources/application.yml 中的数据库连接信息

# 3. 初始化数据库
mysql -u root -p < src/main/resources/db/schema.sql
mysql -u root -p < src/main/resources/db/data.sql

# 4. 运行项目
./mvnw spring-boot:run

# 或使用 Maven
mvn spring-boot:run
```

后端服务默认运行在 http://localhost:8080

### 前端启动

```bash
# 1. 进入前端目录
cd fund-web

# 2. 安装依赖
npm install

# 3. 启动开发服务器
npm run dev
```

前端服务默认运行在 http://localhost:5173

### 构建生产版本

```bash
# 后端构建
./mvnw clean package

# 前端构建
cd fund-web
npm run build
```

### CLI 工具使用

```bash
# 构建 CLI JAR
./mvnw clean package -DskipTests

# 查看帮助
java -jar target/fund-0.0.1-SNAPSHOT-cli.jar

# 基金查询
java -jar target/fund-0.0.1-SNAPSHOT-cli.jar fund search 白酒
java -jar target/fund-0.0.1-SNAPSHOT-cli.jar fund detail 161725

# 持仓管理
java -jar target/fund-0.0.1-SNAPSHOT-cli.jar position list

# 数据同步（用于定时任务）
java -jar target/fund-0.0.1-SNAPSHOT-cli.jar sync nav          # 同步净值
java -jar target/fund-0.0.1-SNAPSHOT-cli.jar sync estimate     # 快照估值
java -jar target/fund-0.0.1-SNAPSHOT-cli.jar sync all          # 全部同步

# 或使用脚本
./scripts/fund-cli fund search 白酒
```

---

## 项目结构

```
fund/
├── src/main/java/com/qoder/fund/    # 后端源码
│   ├── controller/                  # 控制器层
│   ├── service/                     # 业务逻辑层
│   ├── mapper/                      # 数据访问层
│   ├── entity/                      # 实体类
│   ├── dto/                         # 数据传输对象
│   ├── config/                      # 配置类
│   ├── datasource/                  # 数据源（多源估值）
│   ├── scheduler/                   # 定时任务
│   └── cli/                         # CLI 命令行工具
├── src/main/resources/
│   ├── db/                          # 数据库脚本
│   │   ├── schema.sql               # 表结构
│   │   └── data.sql                 # 初始数据
│   └── application.yml              # 应用配置
├── fund-web/                        # 前端项目
│   ├── src/
│   │   ├── api/                     # API 接口
│   │   ├── components/              # 公共组件
│   │   ├── pages/                   # 页面组件
│   │   ├── hooks/                   # 自定义 Hooks
│   │   ├── store/                   # 状态管理
│   │   └── utils/                   # 工具函数
│   └── package.json
├── docs/                            # 文档
├── pom.xml                          # Maven 配置
└── README.md                        # 项目说明
```

---

## API 文档

### 主要接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/funds/search` | GET | 基金搜索 |
| `/api/funds/{code}` | GET | 基金详情 |
| `/api/funds/{code}/nav-history` | GET | 净值历史 |
| `/api/funds/{code}/estimate` | GET | 实时估值 |
| `/api/positions` | GET/POST | 持仓列表/添加 |
| `/api/positions/{id}` | PUT/DELETE | 持仓修改/删除 |
| `/api/watchlist` | GET/POST | 自选列表/添加 |
| `/api/dashboard` | GET | 首页数据 |
| `/api/dashboard/profit-analysis` | GET | 收益分析（曲线+回撤） |
| `/api/accounts` | GET/POST | 账户列表/创建 |

详细 API 文档请参考 [SPEC.md](./SPEC.md)

---

## 开发规范

### 代码风格

- **Java**: 遵循 Spring Boot 惯例，使用 Lombok 简化 POJO
- **TypeScript**: 严格类型检查，接口定义优先
- **Git**: 提交信息格式 `<type>: <description>` (feat/fix/docs/refactor)

### 架构约束

1. **分层边界**: Controller → Service → Mapper，禁止跨层调用
2. **依赖方向**: 外层依赖内层，禁止反向依赖
3. **DTO 规范**: Request/Response DTO 分离，禁止直接使用 Entity 作为 API 返回
4. **异常处理**: 统一使用 GlobalExceptionHandler，禁止在 Controller 中捕获异常

---

## 测试

```bash
# 运行后端测试
./mvnw test

# 前端代码检查
cd fund-web
npm run lint
```

---

## 文档

- [产品需求文档 (PRD)](./PRD.md) - 功能需求、用户故事
- [技术规格文档 (SPEC)](./SPEC.md) - 架构设计、API 规范
- [任务清单 (TODOS)](./TODOS.md) - 开发进度、待办事项
- [开发指南 (AGENTS)](./AGENTS.md) - AI Agent 开发指南

---

## 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feat/amazing-feature`)
3. 提交更改 (`git commit -m 'feat: add amazing feature'`)
4. 推送到分支 (`git push origin feat/amazing-feature`)
5. 创建 Pull Request

---

## 免责声明

本项目提供的基金数据和分析仅供参考，不构成投资建议。投资有风险，入市需谨慎。用户应独立做出投资决策，并承担相应风险。

---

## License

[MIT](LICENSE)

---

<p align="center">
  Made with ❤️ for investors
</p>
