import React, { useEffect, useState } from 'react';
import { Card, Tabs, Button, Divider, Popconfirm, Modal, Form, Select, InputNumber, DatePicker, message } from 'antd';
import { PlusOutlined, DeleteOutlined, TransactionOutlined, PieChartOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import ReactECharts from 'echarts-for-react';
import dayjs from 'dayjs';
import { positionApi, accountApi, type AccountItem, type AddTransactionParams } from '../../api/position';
import type { PositionItem } from '../../api/dashboard';
import { formatAmount, getProfitColor, formatFundType, getFundTypeColor } from '../../utils/format';
import { useAmountVisible } from '../../hooks/useAmountVisible';
import PriceChange from '../../components/PriceChange';
import EmptyGuide from '../../components/EmptyGuide';
import PageSkeleton from '../../components/PageSkeleton';

const Portfolio: React.FC = () => {
  const [positions, setPositions] = useState<PositionItem[]>([]);
  const [accounts, setAccounts] = useState<AccountItem[]>([]);
  const [activeAccount, setActiveAccount] = useState<string>('all');
  const [loading, setLoading] = useState(true);
  const [txModalOpen, setTxModalOpen] = useState(false);
  const [txPositionId, setTxPositionId] = useState<number | null>(null);
  const [txLoading, setTxLoading] = useState(false);
  const [txForm] = Form.useForm();
  const { visible } = useAmountVisible();
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

  const handleDelete = (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    positionApi.remove(id).then(() => {
      message.success('已删除');
      loadData();
    }).catch(() => {});
  };

  const openTxModal = (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    setTxPositionId(id);
    txForm.resetFields();
    setTxModalOpen(true);
  };

  const handleTxSubmit = async () => {
    if (txPositionId == null) return;
    try {
      const values = await txForm.validateFields();
      setTxLoading(true);
      const data: AddTransactionParams = {
        type: values.type,
        amount: values.amount,
        shares: values.shares,
        price: values.price,
        fee: values.fee || 0,
        tradeDate: values.tradeDate.format('YYYY-MM-DD'),
      };
      await positionApi.addTransaction(txPositionId, data);
      message.success('交易记录已添加');
      setTxModalOpen(false);
      loadData();
    } catch {
      // validation or API error handled by interceptor
    } finally {
      setTxLoading(false);
    }
  };

  if (loading) return <PageSkeleton type="dashboard" />;
  if (positions.length === 0) return <EmptyGuide />;

  const filtered = activeAccount === 'all'
    ? positions
    : positions.filter((p) => String(p.accountId) === activeAccount);

  // Summary stats
  const totalMarketValue = filtered.reduce((s, p) => s + p.marketValue, 0);
  const totalProfit = filtered.reduce((s, p) => s + p.profit, 0);
  const totalProfitRate = totalMarketValue > 0 ? (totalProfit / (totalMarketValue - totalProfit)) * 100 : 0;

  // Asset distribution pie chart
  const typeMap: Record<string, number> = {};
  filtered.forEach((p) => {
    const label = formatFundType(p.fundType);
    typeMap[label] = (typeMap[label] || 0) + p.marketValue;
  });
  const pieOption = {
    tooltip: { trigger: 'item' as const, formatter: '{b}: \u00a5{c} ({d}%)' },
    legend: { bottom: 0, textStyle: { fontSize: 12 } },
    series: [{
      type: 'pie',
      radius: ['45%', '72%'],
      center: ['50%', '45%'],
      data: Object.entries(typeMap).map(([name, value]) => ({ name, value: +value.toFixed(2) })),
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 13, fontWeight: 600 } },
      itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 6 },
    }],
  };

  const tabItems = [
    { key: 'all', label: `\u5168\u90e8 (${positions.length})` },
    ...accounts.map((a) => ({
      key: String(a.id),
      label: `${a.name} (${positions.filter(p => String(p.accountId) === String(a.id)).length})`,
    })),
  ];

  return (
    <div className="fund-fade-in">
      {/* Summary Stats */}
      <Card className="fund-card-static" style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', gap: 40, alignItems: 'center' }}>
          <div className="fund-stat-divider">
            <div className="fund-stat-label">{'\u603b\u5e02\u503c'}</div>
            <div className="fund-stat-value" style={{ fontSize: 28, color: 'var(--color-primary)' }}>
              {visible ? `\u00a5${formatAmount(totalMarketValue)}` : '****'}
            </div>
          </div>
          <div className="fund-stat-divider">
            <div className="fund-stat-label">{'\u6301\u6709\u6536\u76ca'}</div>
            <div style={{ fontSize: 22, fontWeight: 600, color: getProfitColor(totalProfit) }}>
              {visible ? `\u00a5${formatAmount(totalProfit)}` : '****'}
            </div>
          </div>
          <div>
            <div className="fund-stat-label">{'\u6536\u76ca\u7387'}</div>
            <PriceChange value={totalProfitRate} showBg size="lg" />
          </div>
          <div style={{ marginLeft: 'auto' }}>
            <Button type="primary" icon={<PlusOutlined />} size="large" onClick={() => navigate('/portfolio/add')}>
              {'\u6dfb\u52a0\u6301\u4ed3'}
            </Button>
          </div>
        </div>
      </Card>

      <div style={{ display: 'flex', gap: 16 }}>
        {/* Position List */}
        <Card className="fund-card-static" style={{ flex: 1 }}>
          <Tabs items={tabItems} activeKey={activeAccount} onChange={setActiveAccount} />
          {filtered.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--color-text-secondary)' }}>
              {'\u8be5\u8d26\u6237\u6682\u65e0\u6301\u4ed3'}
            </div>
          ) : (
            filtered.map((p) => (
              <div
                key={p.id}
                className="fund-list-item"
                onClick={() => navigate(`/fund/${p.fundCode}`)}
              >
                <div style={{ display: 'flex', alignItems: 'center' }}>
                  <div className="fund-type-bar" style={{ background: getFundTypeColor(p.fundType) }} />
                  <div>
                    <div className="fund-name">{p.fundName}</div>
                    <div className="fund-secondary" style={{ marginTop: 2 }}>
                      <span className="fund-code" style={{ fontSize: 12 }}>{p.fundCode}</span>
                      <Divider type="vertical" />
                      {p.accountName}
                    </div>
                  </div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
                  <div style={{ textAlign: 'right', minWidth: 140 }}>
                    <div style={{ fontWeight: 600, fontSize: 15 }}>
                      {visible ? `\u00a5${formatAmount(p.marketValue)}` : '****'}
                    </div>
                    <div className="fund-secondary" style={{ marginTop: 2, display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 6 }}>
                      <span>{'\u6536\u76ca'}</span>
                      <span style={{ color: getProfitColor(p.profit), fontWeight: 500 }}>
                        {visible ? `\u00a5${formatAmount(p.profit)}` : '****'}
                      </span>
                      <PriceChange value={p.profitRate} size="sm" />
                    </div>
                    <div className="fund-secondary" style={{ marginTop: 1 }}>
                      {'\u4eca\u65e5\u4f30\u503c'} <PriceChange value={p.estimateReturn} size="sm" />
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: 4 }} onClick={(e) => e.stopPropagation()}>
                    <Button
                      size="small"
                      icon={<TransactionOutlined />}
                      onClick={(e) => openTxModal(p.id, e)}
                    >
                      {'\u4ea4\u6613'}
                    </Button>
                    <Popconfirm
                      title={'\u786e\u5b9a\u5220\u9664\u8be5\u6301\u4ed3\uff1f'}
                      description={'\u5220\u9664\u540e\u5173\u8054\u7684\u4ea4\u6613\u8bb0\u5f55\u4e5f\u4f1a\u4e00\u5e76\u5220\u9664'}
                      onConfirm={(e) => handleDelete(p.id, e as unknown as React.MouseEvent)}
                      onCancel={(e) => e?.stopPropagation()}
                    >
                      <Button size="small" danger icon={<DeleteOutlined />} className="fund-action-hidden" onClick={(e) => e.stopPropagation()} />
                    </Popconfirm>
                  </div>
                </div>
              </div>
            ))
          )}
        </Card>

        {/* Asset Distribution */}
        <Card
          className="fund-card-static"
          title={<span><PieChartOutlined style={{ marginRight: 8, color: 'var(--color-primary)' }} />{'\u8d44\u4ea7\u5206\u5e03'}</span>}
          style={{ width: 360, flexShrink: 0 }}
        >
          <ReactECharts option={pieOption} theme="fundTheme" style={{ height: 280 }} />
        </Card>
      </div>

      {/* Transaction Modal */}
      <Modal
        title={'\u6dfb\u52a0\u4ea4\u6613\u8bb0\u5f55'}
        open={txModalOpen}
        onOk={handleTxSubmit}
        onCancel={() => setTxModalOpen(false)}
        confirmLoading={txLoading}
        destroyOnClose
        styles={{ body: { paddingTop: 16 } }}
      >
        <Form form={txForm} layout="vertical" initialValues={{ type: 'BUY', tradeDate: dayjs() }}>
          <Form.Item name="type" label={'\u4ea4\u6613\u7c7b\u578b'} rules={[{ required: true }]}>
            <Select options={[
              { value: 'BUY', label: '\u4e70\u5165(\u52a0\u4ed3)' },
              { value: 'SELL', label: '\u5356\u51fa(\u51cf\u4ed3)' },
              { value: 'DIVIDEND', label: '\u5206\u7ea2' },
            ]} />
          </Form.Item>
          <Form.Item name="amount" label={'\u4ea4\u6613\u91d1\u989d(\u5143)'} rules={[{ required: true, message: '\u8bf7\u8f93\u5165\u4ea4\u6613\u91d1\u989d' }]}>
            <InputNumber style={{ width: '100%' }} min={0} precision={2} placeholder={'\u8bf7\u8f93\u5165\u91d1\u989d'} />
          </Form.Item>
          <Form.Item name="shares" label={'\u4ea4\u6613\u4efd\u989d'} rules={[{ required: true, message: '\u8bf7\u8f93\u5165\u4ea4\u6613\u4efd\u989d' }]}>
            <InputNumber style={{ width: '100%' }} min={0} precision={2} placeholder={'\u8bf7\u8f93\u5165\u4efd\u989d'} />
          </Form.Item>
          <Form.Item name="price" label={'\u6210\u4ea4\u51c0\u503c'} rules={[{ required: true, message: '\u8bf7\u8f93\u5165\u6210\u4ea4\u51c0\u503c' }]}>
            <InputNumber style={{ width: '100%' }} min={0} precision={4} placeholder={'\u8bf7\u8f93\u5165\u51c0\u503c'} />
          </Form.Item>
          <Form.Item name="fee" label={'\u624b\u7eed\u8d39(\u5143)'}>
            <InputNumber style={{ width: '100%' }} min={0} precision={2} placeholder={'\u9009\u586b'} />
          </Form.Item>
          <Form.Item name="tradeDate" label={'\u4ea4\u6613\u65e5\u671f'} rules={[{ required: true, message: '\u8bf7\u9009\u62e9\u65e5\u671f' }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default Portfolio;
