-- 基金基本信息
CREATE TABLE IF NOT EXISTS fund (
    code            VARCHAR(10)   PRIMARY KEY COMMENT '基金代码',
    name            VARCHAR(100)  NOT NULL COMMENT '基金名称',
    type            VARCHAR(20)   COMMENT '基金类型: STOCK/MIXED/BOND/MONEY/QDII/INDEX',
    company         VARCHAR(100)  COMMENT '基金公司',
    manager         VARCHAR(50)   COMMENT '基金经理',
    establish_date  DATE          COMMENT '成立日期',
    scale           DECIMAL(14,2) COMMENT '基金规模(亿)',
    risk_level      TINYINT       COMMENT '风险等级: 1低/2中低/3中/4中高/5高',
    fee_rate        JSON          COMMENT '费率信息JSON: {buy,sell,manage,custody}',
    top_holdings    JSON          COMMENT '十大重仓股JSON',
    all_holdings    JSON          COMMENT '完整持仓JSON(年报/半年报)',
    industry_dist   JSON          COMMENT '行业分布JSON',
    holdings_date   DATE          COMMENT '持仓数据披露日期',
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_type (type),
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基金基本信息';

-- 基金净值历史
CREATE TABLE IF NOT EXISTS fund_nav (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    fund_code       VARCHAR(10)   NOT NULL COMMENT '基金代码',
    nav_date        DATE          NOT NULL COMMENT '净值日期',
    nav             DECIMAL(10,4) NOT NULL COMMENT '单位净值',
    acc_nav         DECIMAL(10,4) COMMENT '累计净值',
    daily_return    DECIMAL(8,4)  COMMENT '日涨跌幅(%)',
    UNIQUE KEY uk_code_date (fund_code, nav_date),
    INDEX idx_fund_code (fund_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基金净值历史';

-- 投资账户
CREATE TABLE IF NOT EXISTS account (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(50)   NOT NULL COMMENT '账户名称',
    platform        VARCHAR(50)   COMMENT '平台: alipay/wechat/ttfund/other',
    icon            VARCHAR(20)   COMMENT '图标标识',
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资账户';

-- 基金持仓
CREATE TABLE IF NOT EXISTS position (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    account_id      BIGINT        COMMENT '所属账户ID',
    fund_code       VARCHAR(10)   NOT NULL COMMENT '基金代码',
    shares          DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '持有份额',
    cost_amount     DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '持仓成本(元)',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_fund_code (fund_code),
    INDEX idx_account_id (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基金持仓';

-- 交易记录
CREATE TABLE IF NOT EXISTS fund_transaction (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    position_id     BIGINT        NOT NULL COMMENT '关联持仓ID',
    fund_code       VARCHAR(10)   NOT NULL COMMENT '基金代码',
    type            VARCHAR(10)   NOT NULL COMMENT '交易类型: BUY/SELL/DIVIDEND',
    amount          DECIMAL(14,2) COMMENT '交易金额(元)',
    shares          DECIMAL(14,2) COMMENT '交易份额',
    price           DECIMAL(10,4) COMMENT '成交净值',
    fee             DECIMAL(10,2) DEFAULT 0 COMMENT '手续费',
    trade_date      DATE          NOT NULL COMMENT '交易日期',
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_position_id (position_id),
    INDEX idx_trade_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易记录';

-- 自选基金
CREATE TABLE IF NOT EXISTS watchlist (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    fund_code       VARCHAR(10)   NOT NULL COMMENT '基金代码',
    group_name      VARCHAR(50)   DEFAULT '默认' COMMENT '分组名称',
    created_at      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_group (fund_code, group_name),
    INDEX idx_group (group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自选基金';

-- 估值预测准确度追踪
CREATE TABLE IF NOT EXISTS estimate_prediction (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    fund_code       VARCHAR(10)   NOT NULL COMMENT '基金代码',
    source_key      VARCHAR(20)   NOT NULL COMMENT '数据源: eastmoney/sina/tencent/stock',
    predict_date    DATE          NOT NULL COMMENT '预测日期',
    predicted_nav   DECIMAL(10,4) COMMENT '预测净值',
    predicted_return DECIMAL(8,4) COMMENT '预测涨跌幅(%)',
    actual_nav      DECIMAL(10,4) COMMENT '实际净值',
    actual_return   DECIMAL(8,4)  COMMENT '实际涨跌幅(%)',
    return_error    DECIMAL(8,4)  COMMENT '涨跌幅误差(预测-实际)',
    UNIQUE KEY uk_fund_source_date (fund_code, source_key, predict_date),
    INDEX idx_fund_date (fund_code, predict_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='估值预测准确度追踪';
