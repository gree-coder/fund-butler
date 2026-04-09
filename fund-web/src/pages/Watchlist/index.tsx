import React, { useEffect, useState } from 'react';
import { Card, Tabs, Button, Popconfirm, Empty, message, Tag, Popover } from 'antd';
import { StarFilled, DeleteOutlined, InfoCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { watchlistApi, type WatchlistItem } from '../../api/watchlist';
import { formatNav } from '../../utils/format';
import PriceChange from '../../components/PriceChange';
import PageSkeleton from '../../components/PageSkeleton';

const SOURCE_LABELS: Record<string, string> = {
  eastmoney: '天天基金',
  sina: '新浪财经',
  stock: '重仓股',
};

const SCENARIO_REASONS: Record<string, string> = {
  'ETF实时': '该基金为ETF指数基金，可获取二级市场实时交易价格（精度极高），因此以ETF实时价格为主导权重。',
  '货币基金估值参考意义有限': '货币基金日涨幅约0.01%，波动极小，实时估值参考意义有限，建议关注七日年化收益率。',
  '债券基金估值波动较小': '债券基金波动相对较小，机构估值较为可靠，实时估值可能存在小幅偏差。',
  '固收类': '该基金为债券/货币型基金，股票仓位极低，重仓股估算参考价值有限，因此以机构估值为主。',
  'QDII': '该基金为QDII基金（投资海外市场），海外持仓行情获取不完整，重仓股估算可靠性较低，因此以机构估值为主。',
  '权益高覆盖': '该基金为权益类基金，重仓股持仓覆盖率较高（≥60%），重仓股实时行情估算可信度高，因此给予重仓股较高权重。',
  '权益中覆盖': '该基金为权益类基金，重仓股持仓覆盖率中等（30%-60%），估算存在一定偏差，因此适当降低重仓股权重，以机构估值为主。',
  '权益低覆盖': '该基金为权益类基金，但重仓股持仓覆盖率较低（<30%），重仓股估算可信度不足，因此将重仓股权重降至最低。',
};

const SmartStrategyPopover: React.FC<{ item: WatchlistItem }> = ({ item }) => {
  if (!item.smartStrategyType) return null;

  const content = (
    <div style={{ maxWidth: 300, fontSize: 12 }}>
      <div style={{ fontWeight: 600, marginBottom: 8, fontSize: 13 }}>
        策略: 自适应加权{item.smartAccuracyEnhanced ? ' + 准确度修正' : ''}
      </div>
      <div style={{
        color: 'var(--color-text-secondary)',
        padding: '8px 10px',
        marginBottom: 8,
        background: '#fafbfc',
        borderRadius: 'var(--radius-sm)',
      }}>
        {(item.smartScenario && SCENARIO_REASONS[item.smartScenario]) ||
          '多数据源加权平均，权重根据基金类型和持仓覆盖率自动调整。'}
      </div>
      {item.smartAccuracyEnhanced && (
        <div style={{ color: 'var(--color-primary)', marginBottom: 8, fontSize: 11, fontStyle: 'italic' }}>
          *已根据近3个交易日各源预测误差(MAE)动态修正权重，历史准确度高的数据源权重自动上调。
        </div>
      )}
      {item.smartWeights && (
        <div>
          <div style={{ fontWeight: 500, marginBottom: 6, fontSize: 12, color: 'var(--color-text-primary)' }}>权重构成:</div>
          {Object.entries(item.smartWeights).map(([key, weight]) => (
            <div key={key} style={{ display: 'flex', alignItems: 'center', marginBottom: 4 }}>
              <span style={{ width: 60, fontSize: 11, color: 'var(--color-text-secondary)' }}>{SOURCE_LABELS[key] || key}</span>
              <div className="fund-weight-bar">
                <div className="fund-weight-bar-fill" style={{ width: `${Math.round(weight * 100)}%` }} />
              </div>
              <span style={{ width: 38, textAlign: 'right', fontWeight: 600, fontSize: 12 }}>{Math.round(weight * 100)}%</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );

  return (
    <Popover content={content} title={null} trigger="click" placement="bottom">
      <InfoCircleOutlined
        style={{ marginLeft: 4, color: 'var(--color-primary)', cursor: 'pointer', fontSize: 12 }}
        onClick={(e) => e.stopPropagation()}
      />
    </Popover>
  );
};

const Watchlist: React.FC = () => {
  const [items, setItems] = useState<WatchlistItem[]>([]);
  const [groups, setGroups] = useState<string[]>([]);
  const [activeGroup, setActiveGroup] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  const loadData = (group?: string) => {
    let mounted = true;
    watchlistApi.list(group && group !== '全部' ? group : undefined)
      .then((res) => {
        if (!mounted) return;
        setItems(res.list || []);
        setGroups(res.groups || []);
      })
      .catch(() => {})
      .finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; };
  };

  useEffect(() => {
    const cleanup = loadData();
    return cleanup;
  }, []);

  const handleRemove = async (id: number) => {
    try {
      await watchlistApi.remove(id);
      message.success('已移除');
      loadData(activeGroup);
    } catch { /* handled */ }
  };

  const tabItems = [
    { key: '', label: `全部 (${items.length})` },
    ...groups.map((g) => ({
      key: g,
      label: `${g} (${items.filter(i => i.groupName === g).length})`,
    })),
  ];

  if (loading) return <PageSkeleton type="list" />;

  return (
    <div className="fund-fade-in">
      <Card
        className="fund-card-static"
        title={<span style={{ fontSize: 18, fontWeight: 600 }}><StarFilled style={{ marginRight: 8, color: 'var(--color-watchlist)' }} />自选基金</span>}
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/search')}>添加自选</Button>}
      >
        <Tabs
          items={tabItems}
          activeKey={activeGroup}
          onChange={(key) => { setActiveGroup(key); loadData(key); }}
        />
        {items.length === 0 ? (
          <Empty description="暂无自选基金" style={{ padding: '40px 0' }} />
        ) : (
          items.map((item) => (
            <div
              key={item.id}
              className="fund-list-item"
              onClick={() => navigate(`/fund/${item.fundCode}`)}
            >
              {/* Left: Fund info */}
              <div style={{ flex: 1 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span className="fund-code">{item.fundCode}</span>
                  <span className="fund-name">{item.fundName}</span>
                </div>
                <div className="fund-secondary" style={{ marginTop: 4 }}>
                  净值 <span style={{ fontWeight: 500, color: 'var(--color-text-primary)' }}>{formatNav(item.latestNav)}</span>
                </div>
              </div>

              {/* Center: Smart estimate */}
              <div style={{ flex: 1, textAlign: 'center' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}>
                  <Tag color="blue" style={{ margin: 0, fontSize: 11 }}>智能预估</Tag>
                  <PriceChange value={item.smartEstimateReturn ?? item.estimateReturn} showBg />
                  <SmartStrategyPopover item={item} />
                </div>
                {item.actualReturn != null && (
                  <div style={{ marginTop: 4, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}>
                    <Tag color="gold" style={{ margin: 0, fontSize: 11 }}>{item.actualReturnDelayed ? '实际 T+1' : '实际'}</Tag>
                    <PriceChange value={item.actualReturn} size="sm" />
                  </div>
                )}
              </div>

              {/* Right: Performance + actions */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
                {item.performance && (
                  <div style={{ display: 'flex', gap: 14 }}>
                    <div style={{ textAlign: 'center' }}>
                      <div className="fund-secondary">近1月</div>
                      <div style={{ fontWeight: 500, marginTop: 2 }}>
                        <PriceChange value={item.performance.month1} size="sm" />
                      </div>
                    </div>
                    <div style={{ textAlign: 'center' }}>
                      <div className="fund-secondary">近3月</div>
                      <div style={{ fontWeight: 500, marginTop: 2 }}>
                        <PriceChange value={item.performance.month3} size="sm" />
                      </div>
                    </div>
                    <div style={{ textAlign: 'center' }}>
                      <div className="fund-secondary">近1年</div>
                      <div style={{ fontWeight: 500, marginTop: 2 }}>
                        <PriceChange value={item.performance.year1} size="sm" />
                      </div>
                    </div>
                  </div>
                )}
                <Popconfirm title="确定移除?" onConfirm={(e) => { e?.stopPropagation(); handleRemove(item.id); }}>
                  <Button size="small" danger icon={<DeleteOutlined />} className="fund-action-hidden" onClick={(e) => e.stopPropagation()} />
                </Popconfirm>
              </div>
            </div>
          ))
        )}
      </Card>
    </div>
  );
};

export default Watchlist;
