-- 初始化默认投资账户
INSERT IGNORE INTO account (id, name, platform, icon) VALUES
(1, '支付宝', 'alipay', 'alipay'),
(2, '微信理财通', 'wechat', 'wechat'),
(3, '天天基金', 'ttfund', 'ttfund'),
(4, '蛋卷基金', 'danjuan', 'danjuan'),
(5, '银行', 'bank', 'bank'),
(6, '其他', 'other', 'other');
