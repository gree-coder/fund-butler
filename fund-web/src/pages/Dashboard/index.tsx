import React, { useEffect, useState } from 'react';
import { Card, Col, Row, Statistic, Segmented, Spin } from 'antd';
import { EyeOutlined, EyeInvisibleOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { dashboardApi, type DashboardData, type ProfitTrend } from '../../api/dashboard';
import { useAmountVisible } from '../../hooks/useAmountVisible';
import { formatAmount, formatPercent, getProfitColor } from '../../utils/format';
import PriceChange from '../../components/PriceChange';
import EmptyGuide from '../../components/EmptyGuide';
import ReactECharts from 'echarts-for-react';

const Dashboard: React.FC = () => {
  const [data, setData] = useState<DashboardData | null>(null);
  const [trend, setTrend] = useState<ProfitTrend | null>(null);
  const [trendDays, setTrendDays] = useState<number>(7);
  const [loading, setLoading] = useState(true);
  const { visible, toggle, mask } = useAmountVisible();
  const navigate = useNavigate();

  useEffect(() => {
    setLoading(true);
    dashboardApi.getData()
      .then(setData)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    dashboardApi.getProfitTrend(trendDays)
      .then(setTrend)
      .catch(() => {});
  }, [trendDays]);

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;

  if (!data || data.positions.length === 0) return <EmptyGuide />;

  const trendOption = trend ? {
    tooltip: { trigger: 'axis' as const },
    xAxis: { type: 'category' as const, data: trend.dates, axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value' as const, axisLabel: { formatter: (v: number) => `${v.toFixed(0)}` } },
    series: [{
      type: 'bar',
      data: trend.profits,
      itemStyle: {
        color: (params: { value: number }) => params.value >= 0 ? '#F5222D' : '#52C41A',
      },
    }],
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
  } : {};

  return (
    <div>
      {/* Asset Overview */}
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={24} align="middle">
          <Col span={8}>
            <Statistic
              title={
                <span>
                  总资产{' '}
                  <span onClick={toggle} style={{ cursor: 'pointer', color: '#999' }}>
                    {visible ? <EyeOutlined /> : <EyeInvisibleOutlined />}
                  </span>
                </span>
              }
              value={mask(formatAmount(data.totalAsset))}
              prefix={visible ? '¥' : ''}
              valueStyle={{ fontSize: 28, fontWeight: 700 }}
            />
          </Col>
          <Col span={8}>
            <Statistic
              title="总收益"
              value={mask(formatAmount(data.totalProfit))}
              prefix={visible ? '¥' : ''}
              valueStyle={{ color: getProfitColor(data.totalProfit) }}
              suffix={visible ? <span style={{ fontSize: 14 }}>{formatPercent(data.totalProfitRate)}</span> : null}
            />
          </Col>
          <Col span={8}>
            <Statistic
              title="今日收益"
              value={mask(formatAmount(data.todayProfit))}
              prefix={visible ? '¥' : ''}
              valueStyle={{ color: getProfitColor(data.todayProfit) }}
            />
          </Col>
        </Row>
      </Card>

      <Row gutter={16}>
        {/* Position List */}
        <Col span={14}>
          <Card
            title="持仓基金"
            extra={
              <PlusOutlined
                style={{ cursor: 'pointer', color: '#1677FF' }}
                onClick={() => navigate('/portfolio/add')}
              />
            }
          >
            {data.positions.map((p) => (
              <div
                key={p.id}
                onClick={() => navigate(`/fund/${p.fundCode}`)}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '12px 0',
                  borderBottom: '1px solid #f0f0f0',
                  cursor: 'pointer',
                }}
              >
                <div>
                  <div style={{ fontWeight: 500 }}>{p.fundName}</div>
                  <div style={{ fontSize: 12, color: '#999' }}>{p.fundCode} | {p.accountName}</div>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <div style={{ fontWeight: 500 }}>{visible ? `¥${formatAmount(p.marketValue)}` : '****'}</div>
                  <div style={{ fontSize: 12 }}>
                    <PriceChange value={p.estimateReturn} />
                    <span style={{ color: getProfitColor(p.profitRate), marginLeft: 8 }}>
                      {visible ? formatPercent(p.profitRate) : '****'}
                    </span>
                  </div>
                </div>
              </div>
            ))}
          </Card>
        </Col>

        {/* Profit Trend */}
        <Col span={10}>
          <Card
            title="收益趋势"
            extra={
              <Segmented
                size="small"
                options={[
                  { label: '近7天', value: 7 },
                  { label: '近30天', value: 30 },
                ]}
                value={trendDays}
                onChange={(v) => setTrendDays(v as number)}
              />
            }
          >
            {trend ? <ReactECharts option={trendOption} style={{ height: 250 }} /> : <Spin />}
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Dashboard;
