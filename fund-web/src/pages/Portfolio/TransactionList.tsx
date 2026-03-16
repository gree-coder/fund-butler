import React, { useEffect, useState } from 'react';
import { Card, Timeline, Tag, Spin, Empty } from 'antd';
import { positionApi, type TransactionItem } from '../../api/position';
import { formatAmount } from '../../utils/format';

const TransactionList: React.FC = () => {
  const [transactions, setTransactions] = useState<TransactionItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Load all transactions for all positions
    positionApi.list()
      .then(async (positions) => {
        const all: TransactionItem[] = [];
        for (const p of positions || []) {
          try {
            const txns = await positionApi.getTransactions(p.id);
            all.push(...(txns || []));
          } catch { /* skip */ }
        }
        all.sort((a, b) => b.tradeDate.localeCompare(a.tradeDate));
        setTransactions(all);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;

  if (transactions.length === 0) return <Empty description="暂无交易记录" />;

  const typeColor: Record<string, string> = { BUY: 'red', SELL: 'green', DIVIDEND: 'gold' };
  const typeLabel: Record<string, string> = { BUY: '买入', SELL: '卖出', DIVIDEND: '分红' };

  return (
    <Card title="交易记录">
      <Timeline
        items={transactions.map((t) => ({
          color: typeColor[t.type] || 'blue',
          children: (
            <div>
              <div>
                <Tag color={typeColor[t.type]}>{typeLabel[t.type] || t.type}</Tag>
                <strong>{t.fundCode}</strong>
                <span style={{ marginLeft: 8, color: '#999' }}>{t.tradeDate}</span>
              </div>
              <div style={{ fontSize: 13, color: '#666', marginTop: 4 }}>
                金额: ¥{formatAmount(t.amount)} | 份额: {t.shares} | 净值: {t.price}
                {t.fee > 0 && ` | 手续费: ¥${formatAmount(t.fee)}`}
              </div>
            </div>
          ),
        }))}
      />
    </Card>
  );
};

export default TransactionList;
