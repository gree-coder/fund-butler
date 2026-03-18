import React, { useEffect, useState } from 'react';
import { Card, Tag, Empty } from 'antd';
import { SwapOutlined, ArrowUpOutlined, ArrowDownOutlined, GiftOutlined } from '@ant-design/icons';
import { positionApi, type TransactionItem } from '../../api/position';
import { formatAmount } from '../../utils/format';
import PageSkeleton from '../../components/PageSkeleton';

const TransactionList: React.FC = () => {
  const [transactions, setTransactions] = useState<TransactionItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
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

  if (loading) return <PageSkeleton type="list" />;

  if (transactions.length === 0) return (
    <Card className="fund-card-static">
      <Empty description="暂无交易记录" />
    </Card>
  );

  const typeConfig: Record<string, { color: string; label: string; icon: React.ReactNode; dotColor: string }> = {
    BUY: { color: 'red', label: '买入', icon: <ArrowDownOutlined />, dotColor: 'var(--color-profit)' },
    SELL: { color: 'green', label: '卖出', icon: <ArrowUpOutlined />, dotColor: 'var(--color-loss)' },
    DIVIDEND: { color: 'gold', label: '分红', icon: <GiftOutlined />, dotColor: '#faad14' },
  };

  // Group by date
  const grouped: Record<string, TransactionItem[]> = {};
  transactions.forEach((t) => {
    const date = t.tradeDate;
    if (!grouped[date]) grouped[date] = [];
    grouped[date].push(t);
  });

  return (
    <div className="fund-fade-in">
      <Card
        className="fund-card-static"
        title={<span><SwapOutlined style={{ marginRight: 8, color: 'var(--color-primary)' }} />交易记录</span>}
      >
        {Object.entries(grouped).map(([date, txns]) => (
          <div key={date} style={{ marginBottom: 20 }}>
            <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 8, paddingLeft: 4 }}>
              {date}
            </div>
            {txns.map((t) => {
              const cfg = typeConfig[t.type] || { color: 'blue', label: t.type, icon: null, dotColor: '#1677ff' };
              return (
                <div key={t.id} className="fund-timeline-card" style={{ marginBottom: 6 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      <div style={{
                        width: 28, height: 28, borderRadius: '50%',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        fontSize: 13, color: '#fff', background: cfg.dotColor,
                      }}>
                        {cfg.icon}
                      </div>
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          <Tag color={cfg.color} style={{ margin: 0 }}>{cfg.label}</Tag>
                          <span className="fund-code" style={{ fontSize: 13 }}>{t.fundCode}</span>
                        </div>
                      </div>
                    </div>
                    <div style={{ textAlign: 'right' }}>
                      <div style={{ fontWeight: 600, fontSize: 15 }}>
                        {t.type === 'SELL' ? '+' : '-'}¥{formatAmount(t.amount)}
                      </div>
                    </div>
                  </div>
                  <div className="fund-secondary" style={{ marginTop: 6, paddingLeft: 38, display: 'flex', gap: 16 }}>
                    <span>份额: {t.shares}</span>
                    <span>净值: {t.price}</span>
                    {t.fee > 0 && <span>手续费: ¥{formatAmount(t.fee)}</span>}
                  </div>
                </div>
              );
            })}
          </div>
        ))}
      </Card>
    </div>
  );
};

export default TransactionList;
