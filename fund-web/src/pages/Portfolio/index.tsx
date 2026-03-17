import React, { useEffect, useState } from 'react';
import { Card, Tabs, Button, Spin, Popconfirm, Modal, Form, Select, InputNumber, DatePicker, message } from 'antd';
import { PlusOutlined, DeleteOutlined, TransactionOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import ReactECharts from 'echarts-for-react';
import dayjs from 'dayjs';
import { positionApi, accountApi, type AccountItem, type AddTransactionParams } from '../../api/position';
import type { PositionItem } from '../../api/dashboard';
import { formatAmount, getProfitColor, formatFundType } from '../../utils/format';
import { useAmountVisible } from '../../hooks/useAmountVisible';
import PriceChange from '../../components/PriceChange';
import EmptyGuide from '../../components/EmptyGuide';

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
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
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
              <div style={{ display: 'flex', gap: 4 }} onClick={(e) => e.stopPropagation()}>
                <Button
                  size="small"
                  icon={<TransactionOutlined />}
                  onClick={(e) => openTxModal(p.id, e)}
                >
                  交易
                </Button>
                <Popconfirm
                  title="确定删除该持仓？"
                  description="删除后关联的交易记录也会一并删除"
                  onConfirm={(e) => handleDelete(p.id, e as unknown as React.MouseEvent)}
                  onCancel={(e) => e?.stopPropagation()}
                >
                  <Button size="small" danger icon={<DeleteOutlined />} onClick={(e) => e.stopPropagation()} />
                </Popconfirm>
              </div>
            </div>
          </div>
        ))}
      </Card>

      <Card title="资产分布">
        <ReactECharts option={pieOption} style={{ height: 300 }} />
      </Card>

      {/* Transaction Modal */}
      <Modal
        title="添加交易记录"
        open={txModalOpen}
        onOk={handleTxSubmit}
        onCancel={() => setTxModalOpen(false)}
        confirmLoading={txLoading}
        destroyOnClose
      >
        <Form form={txForm} layout="vertical" initialValues={{ type: 'BUY', tradeDate: dayjs() }}>
          <Form.Item name="type" label="交易类型" rules={[{ required: true }]}>
            <Select options={[
              { value: 'BUY', label: '买入(加仓)' },
              { value: 'SELL', label: '卖出(减仓)' },
              { value: 'DIVIDEND', label: '分红' },
            ]} />
          </Form.Item>
          <Form.Item name="amount" label="交易金额(元)" rules={[{ required: true, message: '请输入交易金额' }]}>
            <InputNumber style={{ width: '100%' }} min={0} precision={2} placeholder="请输入金额" />
          </Form.Item>
          <Form.Item name="shares" label="交易份额" rules={[{ required: true, message: '请输入交易份额' }]}>
            <InputNumber style={{ width: '100%' }} min={0} precision={2} placeholder="请输入份额" />
          </Form.Item>
          <Form.Item name="price" label="成交净值" rules={[{ required: true, message: '请输入成交净值' }]}>
            <InputNumber style={{ width: '100%' }} min={0} precision={4} placeholder="请输入净值" />
          </Form.Item>
          <Form.Item name="fee" label="手续费(元)">
            <InputNumber style={{ width: '100%' }} min={0} precision={2} placeholder="选填" />
          </Form.Item>
          <Form.Item name="tradeDate" label="交易日期" rules={[{ required: true, message: '请选择日期' }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default Portfolio;
