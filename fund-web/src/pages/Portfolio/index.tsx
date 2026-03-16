import React, { useEffect, useState } from 'react';
import { Card, Tabs, Button, Spin } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import ReactECharts from 'echarts-for-react';
import { positionApi, accountApi, type AccountItem } from '../../api/position';
import type { PositionItem } from '../../api/dashboard';
import { formatAmount, formatPercent, getProfitColor, formatFundType } from '../../utils/format';
import { useAmountVisible } from '../../hooks/useAmountVisible';
import PriceChange from '../../components/PriceChange';
import EmptyGuide from '../../components/EmptyGuide';

const Portfolio: React.FC = () => {
  const [positions, setPositions] = useState<PositionItem[]>([]);
  const [accounts, setAccounts] = useState<AccountItem[]>([]);
  const [activeAccount, setActiveAccount] = useState<string>('all');
  const [loading, setLoading] = useState(true);
  const { visible, toggle, mask } = useAmountVisible();
  const navigate = useNavigate();

  const loadData = () => {
    setLoading(true);
    Promise.all([positionApi.list(), accountApi.list()])
      .then(([pos, acc]) => {
        setPositions(pos || []);
        setAccounts(acc || []);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  };

  useEffect(() => { loadData(); }, []);

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;
  if (positions.length === 0) return <EmptyGuide />;

  const filtered = activeAccount === 'all'
    ? positions
    : positions.filter((p) => String(p.accountId) === activeAccount);

  // Asset distribution pie chart
  const typeMap: Record<string, number> = {};
  filtered.forEach((p) => {
    const label = formatFundType(p.fundType);
    typeMap[label] = (typeMap[label] || 0) + p.marketValue;
  });
  const pieOption = {
    tooltip: { trigger: 'item' as const },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: Object.entries(typeMap).map(([name, value]) => ({ name, value: +value.toFixed(2) })),
      label: { fontSize: 11 },
    }],
  };

  const tabItems = [
    { key: 'all', label: '全部' },
    ...accounts.map((a) => ({ key: String(a.id), label: a.name })),
  ];

  return (
    <div>
      <Card
        title="我的持仓"
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/portfolio/add')}>添加持仓</Button>}
        style={{ marginBottom: 16 }}
      >
        <Tabs items={tabItems} activeKey={activeAccount} onChange={setActiveAccount} />
        {filtered.map((p) => (
          <div
            key={p.id}
            onClick={() => navigate(`/fund/${p.fundCode}`)}
            style={{
              display: 'flex', justifyContent: 'space-between', alignItems: 'center',
              padding: '12px 0', borderBottom: '1px solid #f0f0f0', cursor: 'pointer',
            }}
          >
            <div>
              <div style={{ fontWeight: 500 }}>{p.fundName}</div>
              <div style={{ fontSize: 12, color: '#999' }}>{p.fundCode} | {p.accountName}</div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div>{visible ? `¥${formatAmount(p.marketValue)}` : '****'}</div>
              <div style={{ fontSize: 12 }}>
                <span style={{ color: '#999' }}>持有收益 </span>
                <span style={{ color: getProfitColor(p.profit) }}>
                  {visible ? `¥${formatAmount(p.profit)}` : '****'}
                </span>
                <PriceChange value={p.profitRate} style={{ marginLeft: 4 }} />
              </div>
              <div style={{ fontSize: 12, color: '#999' }}>
                今日估值 <PriceChange value={p.estimateReturn} />
              </div>
            </div>
          </div>
        ))}
      </Card>

      <Card title="资产分布">
        <ReactECharts option={pieOption} style={{ height: 300 }} />
      </Card>
    </div>
  );
};

export default Portfolio;
