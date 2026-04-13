# 基金管家 - AI Agent 开发指南

> 本文档面向 AI Agent，描述项目结构、开发规范和协作流程。

## 项目概述

**基金管家**是一款面向个人投资者的基金管理与查询 Web 应用，定位为"一站式基金数据聚合管理工具"。

- **后端**：Spring Boot 3.4.3 + Java 17 + MyBatis-Plus + MySQL
- **前端**：React 19 + TypeScript + Vite + Ant Design 6 + ECharts 5
- **CLI**：Picocli 4.7.5 + Spring Boot 命令行模式
- **AI 能力**：规则引擎 + 外部 API（禁用 LLM），面向外部 Agent 提供数据供给
- **架构**：前后端分离，RESTful API，支持 Web + CLI 双模式

## 快速导航

| 文档 | 路径 | 说明 |
|------|------|------|
| 产品需求 | [PRD.md](./PRD.md) | 功能需求、用户故事 |
| 技术规格 | [SPEC.md](./SPEC.md) | 架构设计、API 规范 |
| 任务清单 | [TODOS.md](./TODOS.md) | 开发进度、待办事项 |
| 后端代码 | [src/main/java/com/qoder/fund/](./src/main/java/com/qoder/fund/) | Java 源码 |
| 前端代码 | [fund-web/src/](./fund-web/src/) | React/TypeScript 源码 |
| 数据库脚本 | [src/main/resources/db/](./src/main/resources/db/) | schema.sql, data.sql |

## 核心领域模型

```
基金(Fund) ──┬── 净值历史(FundNav)
             ├── 持仓(Position) ── 交易记录(FundTransaction)
             ├── 自选(Watchlist)
             ├── 账户(Account)
             └── AI分析
                  ├── 市场概览(MarketOverview)
                  ├── 基金诊断(FundDiagnosis)
                  ├── 风险预警(RiskWarning)
                  └── 调仓时机(RebalanceTiming)
```

## CLI 命令架构

CLI 支持 7 个主命令组：

| 命令 | 说明 | 子命令 |
|------|------|--------|
| `fund` | 基金查询与管理 | search, detail, nav, estimate, refresh, analysis |
| `position` | 持仓管理 | list, add, delete, buy, sell, transactions |
| `watchlist` | 自选基金 | list, add, remove |
| `dashboard` | 资产概览 | (默认), trend, broadcast |
| `account` | 账户管理 | list, create, delete |
| `sync` | 数据同步 | nav, estimate, holdings, evaluate, compensate, all |
| `ai` | **AI 数据分析** | market, diagnose, risk, positions |

### AI 数据供给接口设计原则

- **仅输出客观事实性指标**，禁止输出主观建议（如“建议增持/减持”）
- 决策权完全交给外部 Agent，CLI 是纯数据层
- 所有输出为 JSON 格式，方便程序化解析

## 开发规范

### 代码风格
- **Java**: 遵循 Spring Boot 惯例，使用 Lombok 简化 POJO
- **TypeScript**: 严格类型检查，接口定义优先
- **Git**: 提交信息格式 `<type>: <description>` (feat/fix/docs/refactor)

### 架构约束（强制检查）
1. **分层边界**: Controller → Service → Mapper，禁止跨层调用
2. **依赖方向**: 外层依赖内层，禁止反向依赖
3. **DTO 规范**: Request/Response DTO 分离，禁止直接使用 Entity 作为 API 返回
4. **异常处理**: 统一使用 GlobalExceptionHandler，禁止在 Controller 中捕获异常

### 测试要求
- 新增功能必须包含单元测试
- API 修改必须更新 API 文档
- 数据库变更必须提供迁移脚本

## Agent 协作流程

### 1. 任务启动
- 阅读 TODOS.md 了解当前进度
- 阅读 SPEC.md 了解技术规范
- 确认需求范围，列出假设和待确认点

### 2. 开发执行
- 遵循"小步快跑"原则，频繁提交
- 每次提交前运行 `./mvnw test` 确保测试通过
- 复杂变更先创建分支，完成后 PR 合并

### 3. 完成验证
- 本地启动验证功能正常
- 检查是否引入重复代码
- 更新 TODOS.md 标记完成项

## 工具链

```bash
# 后端构建
./mvnw clean test          # 运行测试
./mvnw spring-boot:run     # 本地启动 Web 服务

# CLI 构建与使用
./mvnw clean package       # 构建 CLI JAR
java -jar target/fund-0.0.1-SNAPSHOT-cli.jar  # 运行 CLI

# 前端构建
cd fund-web
npm install
npm run dev                # 开发模式
npm run build              # 生产构建
npm run lint               # 代码检查
```

## 常见任务模板

### 添加新 API
1. 在 `dto/request/` 创建 Request DTO
2. 在 `dto/` 创建 Response DTO
3. 在 `controller/` 添加 Controller 方法
4. 在 `service/` 实现业务逻辑
5. 在 `mapper/` 添加数据访问（如需要）
6. 添加单元测试

### 修改数据库
1. 更新 `src/main/resources/db/schema.sql`
2. 添加数据迁移脚本（如需要）
3. 更新对应 Entity
4. 更新 Mapper

## 注意事项

- ⚠️ **禁止**: 直接修改生产数据库
- ⚠️ **禁止**: 在代码中硬编码 API 密钥
- ⚠️ **禁止**: 提交未测试的代码到主分支
- ✅ **必须**: 每次提交前运行测试
- ✅ **必须**: 代码审查通过后才能合并

## Harness Engineering 文件维护

### 自动更新的文件
- `checkstyle.xml` - 规则变更时手动更新，执行时自动检查
- `.eslintrc.cjs` - 规则变更时手动更新，执行时自动检查
- `ci.yml` - 配置变更时手动更新，push 时自动触发
- `pre-commit.sh` - 脚本变更时手动更新，commit 时自动执行

### 需要手动更新的文件
| 文件 | 更新时机 | 负责人 |
|------|---------|--------|
| `AGENTS.md` | 项目结构/技术栈变化 | AI 主动建议 + 用户确认 |
| `PROGRESS.md` | 完成新功能/里程碑 | AI 完成任务后自动更新 |
| `docs/architecture/adr-*.md` | 重大技术决策 | AI 决策时主动创建 |

### 更新检查清单（AI 完成任务后执行）
- [ ] 是否新增了模块/功能？→ 更新 AGENTS.md
- [ ] 是否完成了重要功能？→ 更新 PROGRESS.md
- [ ] 是否做了技术选型/架构决策？→ 创建 ADR
- [ ] 是否需要调整代码规范？→ 建议更新 checkstyle/ESLint

---

*最后更新: 2026-04-13*
