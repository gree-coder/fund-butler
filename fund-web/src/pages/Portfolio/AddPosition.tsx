import React, { useEffect, useState } from 'react';
import { Form, Input, InputNumber, DatePicker, Select, Button, Card, message } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { positionApi, accountApi, type AccountItem } from '../../api/position';
import { fundApi, type FundSearchItem } from '../../api/fund';
import dayjs from 'dayjs';

const AddPosition: React.FC = () => {
  const [form] = Form.useForm();
  const [accounts, setAccounts] = useState<AccountItem[]>([]);
  const [searchResults, setSearchResults] = useState<FundSearchItem[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  useEffect(() => {
    accountApi.list().then(setAccounts).catch(() => {});
    const fundCode = searchParams.get('fundCode');
    if (fundCode) {
      form.setFieldValue('fundCode', fundCode);
    }
  }, [form, searchParams]);

  const handleFundSearch = async (value: string) => {
    if (value.length < 2) { setSearchResults([]); return; }
    try {
      const res = await fundApi.search(value);
      setSearchResults(res.list || []);
    } catch { setSearchResults([]); }
  };

  const handleSubmit = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      const holdingAmount = values.amount as number;
      const profit = values.profit as number;
      const shares = values.shares as number;
      const costAmount = holdingAmount - profit;
      const price = shares > 0 ? costAmount / shares : 0;
      await positionApi.add({
        fundCode: values.fundCode as string,
        accountId: values.accountId as number,
        amount: costAmount,
        shares,
        price,
        tradeDate: (values.tradeDate as dayjs.Dayjs).format('YYYY-MM-DD'),
      });
      message.success('添加成功');
      navigate('/portfolio');
    } catch { /* handled by interceptor */ }
    finally { setSubmitting(false); }
  };

  return (
    <Card title="添加持仓" style={{ maxWidth: 600, margin: '0 auto' }}>
      <Form form={form} layout="vertical" onFinish={handleSubmit} initialValues={{ tradeDate: dayjs() }}>
        <Form.Item name="fundCode" label="基金" rules={[{ required: true, message: '请选择基金' }]}>
          <Select
            showSearch
            placeholder="输入基金代码或名称搜索"
            filterOption={false}
            onSearch={handleFundSearch}
            options={searchResults.map((f) => ({ value: f.code, label: `${f.code} ${f.name}` }))}
          />
        </Form.Item>
        <Form.Item name="accountId" label="所属账户" rules={[{ required: true, message: '请选择账户' }]}>
          <Select placeholder="选择账户" options={accounts.map((a) => ({ value: a.id, label: a.name }))} />
        </Form.Item>
        <Form.Item name="amount" label="持有金额(元)" rules={[{ required: true, message: '请输入持有金额' }]}>
          <InputNumber style={{ width: '100%' }} min={0} precision={2} placeholder="当前持有的总金额" />
        </Form.Item>
        <Form.Item name="profit" label="持有收益(元)" rules={[{ required: true, message: '请输入持有收益' }]}>
          <InputNumber style={{ width: '100%' }} precision={2} placeholder="当前持有收益，亏损填负数" />
        </Form.Item>
        <Form.Item name="shares" label="持有份额" rules={[{ required: true, message: '请输入份额' }]}>
          <InputNumber style={{ width: '100%' }} min={0} precision={2} placeholder="当前持有的份额" />
        </Form.Item>
        <Form.Item name="tradeDate" label="交易日期" rules={[{ required: true, message: '请选择日期' }]}>
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={submitting} block>确认添加</Button>
        </Form.Item>
      </Form>
    </Card>
  );
};

export default AddPosition;
