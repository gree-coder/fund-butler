import React, { useEffect, useState } from 'react';
import { Card, Tabs, List, Button, Popconfirm, Spin, Empty, message } from 'antd';
import { StarOutlined, DeleteOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { watchlistApi, type WatchlistItem } from '../../api/watchlist';
import { formatNav, formatPercent, getProfitColor } from '../../utils/format';
import PriceChange from '../../components/PriceChange';

const Watchlist: React.FC = () => {
  const [items, setItems] = useState<WatchlistItem[]>([]);
  const [groups, setGroups] = useState<string[]>([]);
  const [activeGroup, setActiveGroup] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  const loadData = (group?: string) => {
    setLoading(true);
    watchlistApi.list(group && group !== '全部' ? group : undefined)
      .then((res) => {
        setItems(res.list || []);
        setGroups(res.groups || []);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  };

  useEffect(() => { loadData(); }, []);

  const handleRemove = async (id: number) => {
    try {
      await watchlistApi.remove(id);
      message.success('已移除');
      loadData(activeGroup);
    } catch { /* handled */ }
  };

  const tabItems = [
    { key: '', label: '全部' },
    ...groups.map((g) => ({ key: g, label: g })),
  ];

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;

  return (
    <Card
      title={<><StarOutlined style={{ marginRight: 8 }} />自选基金</>}
      extra={<Button type="primary" onClick={() => navigate('/search')}>添加自选</Button>}
    >
      <Tabs
        items={tabItems}
        activeKey={activeGroup}
        onChange={(key) => { setActiveGroup(key); loadData(key); }}
      />
      {items.length === 0 ? (
        <Empty description="暂无自选基金" />
      ) : (
        <List
          dataSource={items}
          renderItem={(item) => (
            <List.Item
              style={{ cursor: 'pointer' }}
              onClick={() => navigate(`/fund/${item.fundCode}`)}
              actions={[
                <Popconfirm key="del" title="确定移除?" onConfirm={(e) => { e?.stopPropagation(); handleRemove(item.id); }}>
                  <Button size="small" danger icon={<DeleteOutlined />} onClick={(e) => e.stopPropagation()}>
                    移除
                  </Button>
                </Popconfirm>,
              ]}
            >
              <List.Item.Meta
                title={<span><strong>{item.fundCode}</strong> {item.fundName}</span>}
                description={
                  <span style={{ fontSize: 12 }}>
                    净值 {formatNav(item.latestNav)}
                    <span style={{ marginLeft: 12 }}>估值 <PriceChange value={item.estimateReturn} /></span>
                  </span>
                }
              />
              <div style={{ display: 'flex', gap: 16, fontSize: 12 }}>
                {item.performance && (
                  <>
                    <div>近1月 <span style={{ color: getProfitColor(item.performance.month1) }}>{formatPercent(item.performance.month1)}</span></div>
                    <div>近3月 <span style={{ color: getProfitColor(item.performance.month3) }}>{formatPercent(item.performance.month3)}</span></div>
                    <div>近1年 <span style={{ color: getProfitColor(item.performance.year1) }}>{formatPercent(item.performance.year1)}</span></div>
                  </>
                )}
              </div>
            </List.Item>
          )}
        />
      )}
    </Card>
  );
};

export default Watchlist;
