import React, { useEffect, useState, useCallback } from 'react';
import { Form, InputNumber, DatePicker, Select, Button, Card, message, Typography, Divider } from 'antd';
import { ArrowLeftOutlined, InfoCircleOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { positionApi, accountApi, type AccountItem } from '../../api/position';
import { fundApi, type FundSearchItem } from '../../api/fund';
import dayjs from 'dayjs';

const { Text } = Typography;

const AddPosition: React.FC = () => {
  const [form] = Form.useForm();
  const [accounts, setAccounts] = useState<AccountItem[]>([]);
  const [searchResults, setSearchResults] = useState<FundSearchItem[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [latestNav, setLatestNav] = useState<number | null>(null);
  const [syncing, setSyncing] = useState<'profit' | 'profitRate' | null>(null);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  useEffect(() => {
    accountApi.list().then(setAccounts).catch(() => {});
    const fundCode = searchParams.get('fundCode');
    if (fundCode) {
      form.setFieldValue('fundCode', fundCode);
      fetchLatestNav(fundCode);
    }
  }, [form, searchParams]);

  const fetchLatestNav = useCallback(async (code: string) => {
    try {
      const detail = await fundApi.getDetail(code);
      if (detail?.latestNav) {
        setLatestNav(detail.latestNav);
      }
    } catch {
      setLatestNav(null);
    }
  }, []);

  const handleFundSearch = async (value: string) => {
    if (value.length < 2) { setSearchResults([]); return; }
    try {
      const res = await fundApi.search(value);
      setSearchResults(res.list || []);
    } catch { setSearchResults([]); }
  };

  const handleFundChange = (code: string) => {
    setLatestNav(null);
    if (code) {
      fetchLatestNav(code);
    }
  };

  const handleValuesChange = (changed: Record<string, unknown>) => {
    const amount = form.getFieldValue('amount') as number | undefined;

    if ('profit' in changed && syncing !== 'profitRate') {
      const profit = changed.profit as number | undefined;
      if (profit != null && amount != null && amount !== 0) {
        const costAmount = amount - profit;
        if (costAmount > 0) {
          const rate = (profit / costAmount) * 100;
          setSyncing('profit');
          form.setFieldValue('profitRate', Math.round(rate * 100) / 100);
          setTimeout(() => setSyncing(null), 0);
        }
      }
    }

    if ('profitRate' in changed && syncing !== 'profit') {
      const profitRate = changed.profitRate as number | undefined;
      if (profitRate != null && amount != null) {
        const profit = amount * profitRate / (100 + profitRate);
        setSyncing('profitRate');
        form.setFieldValue('profit', Math.round(profit * 100) / 100);
        setTimeout(() => setSyncing(null), 0);
      }
    }

    if ('amount' in changed) {
      const profitRate = form.getFieldValue('profitRate') as number | undefined;
      if (profitRate != null && amount != null) {
        const profit = amount * profitRate / (100 + profitRate);
        form.setFieldValue('profit', Math.round(profit * 100) / 100);
      }
    }
  };

  const calcShares = (): number | null => {
    const amount = form.getFieldValue('amount') as number | undefined;
    if (amount && latestNav && latestNav > 0) {
      return Math.round((amount / latestNav) * 10000) / 10000;
    }
    return null;
  };

  const handleSubmit = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      const holdingAmount = values.amount as number;
      const profit = values.profit as number;
      const costAmount = holdingAmount - profit;

      let shares: number | undefined;
      let price: number | undefined;

      if (latestNav && latestNav > 0) {
        shares = Math.round((holdingAmount / latestNav) * 10000) / 10000;
        price = shares > 0 ? Math.round((costAmount / shares) * 10000) / 10000 : undefined;
      }

      await positionApi.add({
        fundCode: values.fundCode as string,
        accountId: values.accountId as number,
        amount: Math.round(costAmount * 100) / 100,
        shares,
        price,
        tradeDate: (values.tradeDate as dayjs.Dayjs).format('YYYY-MM-DD'),
      });
      message.success('添加成功');
      navigate('/portfolio');
    } catch { /* handled by interceptor */ }
    finally { setSubmitting(false); }
  };

  const estimatedShares = calcShares();

  return (
    <div className="fund-fade-in" style={{ maxWidth: 640, margin: '0 auto' }}>
      <div style={{ marginBottom: 16 }}>
        <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/portfolio')} style={{ color: 'var(--color-text-secondary)' }}>
          返回持仓
        </Button>
      </div>
      <Card className="fund-card-static" title={<span style={{ fontSize: 18, fontWeight: 600 }}>添加持仓</span>}>
        <div className="fund-info-bar" style={{ marginBottom: 20 }}>
          <InfoCircleOutlined style={{ marginRight: 6 }} />
          填写持有金额和收益即可，系统将自动推算持有份额和成本净值
        </div>
        <Form form={form} layout="vertical" onFinish={handleSubmit} onValuesChange={handleValuesChange} initialValues={{ tradeDate: dayjs() }}>
          <Form.Item name="fundCode" label="基金" rules={[{ required: true, message: '请选择基金' }]}>
            <Select
              showSearch
              placeholder="输入基金代码或名称搜索"
              filterOption={false}
              onSearch={handleFundSearch}
              onChange={handleFundChange}
              options={searchResults.map((f) => ({ value: f.code, label: `${f.code} ${f.name}` }))}
              size="large"
            />
          </Form.Item>
          <Form.Item name="accountId" label="所属账户" rules={[{ required: true, message: '请选择账户' }]}>
            <Select placeholder="选择账户" options={accounts.map((a) => ({ value: a.id, label: a.name }))} size="large" />
          </Form.Item>
          <Divider style={{ margin: '8px 0 16px' }} />
          <Form.Item name="amount" label="持有金额(元)" rules={[{ required: true, message: '请输入持有金额' }]}>
            <InputNumber style={{ width: '100%' }} min={0} precision={2} placeholder="当前持有的总金额" size="large" />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="profit" label="持有收益(元)" rules={[{ required: true, message: '请输入持有收益' }]} style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} precision={2} placeholder="亏损填负数" size="large" />
            </Form.Item>
            <Form.Item name="profitRate" label="持有收益率(%)" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} precision={2} placeholder="自动互算" addonAfter="%" size="large" />
            </Form.Item>
          </div>
          {latestNav && (
            <div style={{
              marginBottom: 20,
              padding: '12px 16px',
              background: 'var(--color-primary-light)',
              borderRadius: 'var(--radius-md)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}>
              <Text type="secondary" style={{ fontSize: 13 }}>
                最新净值: <Text strong style={{ color: 'var(--color-primary)' }}>{latestNav.toFixed(4)}</Text>
              </Text>
              {estimatedShares != null && (
                <Text type="secondary" style={{ fontSize: 13 }}>
                  预计份额: <Text strong style={{ color: 'var(--color-primary)' }}>{estimatedShares.toFixed(4)}</Text>
                </Text>
              )}
            </div>
          )}
          <Form.Item name="tradeDate" label="交易日期" rules={[{ required: true, message: '请选择日期' }]}>
            <DatePicker style={{ width: '100%' }} size="large" />
          </Form.Item>
          <Form.Item style={{ marginTop: 8 }}>
            <Button type="primary" htmlType="submit" loading={submitting} block size="large">
              确认添加
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default AddPosition;
