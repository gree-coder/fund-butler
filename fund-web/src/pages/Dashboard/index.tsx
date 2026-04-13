import React, { useEffect, useState } from 'react';
import { Card, Col, Row, Statistic, Segmented, Tooltip } from 'antd';
import { EyeOutlined, EyeInvisibleOutlined, PlusOutlined, RightOutlined, QuestionCircleOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { dashboardApi, type DashboardData, type ProfitTrend } from '../../api/dashboard';
import { useAmountVisible } from '../../hooks/useAmountVisible';
import { formatAmount, formatPercent, getProfitColor, getFundTypeColor } from '../../utils/format';
import PriceChange from '../../components/PriceChange';
import EmptyGuide from '../../components/EmptyGuide';
import PageSkeleton from '../../components/PageSkeleton';
import RiskWarningCard from '../../components/RiskWarningCard';
import RebalanceTimingCard from '../../components/RebalanceTimingCard';
import MarketOverviewCard from '../../components/MarketOverviewCard';
import ReactECharts from 'echarts-for-react';

const Dashboard: React.FC = () => {
  const [data, setData] = useState<DashboardData | null>(null);
  const [trend, setTrend] = useState<ProfitTrend | null>(null);
  const [trendDays, setTrendDays] = useState<number>(7);
  const [loading, setLoading] = useState(true);
  const { visible, toggle, mask } = useAmountVisible();
  const navigate = useNavigate();

  // 并行加载 Dashboard 数据和收益趋势
  useEffect(() => {
    let mounted = true;
    
    // 并行发起两个请求，减少总体等待时间
    Promise.all([
      dashboardApi.getData(),
      dashboardApi.getProfitTrend(trendDays)
    ]).then(([dashboardData, trendData]) => {
      if (mounted) {
        setData(dashboardData);
        setTrend(trendData);
        setLoading(false);
      }
    }).catch(() => {
      if (mounted) setLoading(false);
    });
    
    return () => { mounted = false; };
  }, []);

  // 趋势天数变化时只重新加载趋势数据
  useEffect(() => {
    // 避免首次加载时重复请求（已在上面处理）
    if (trend && trend.dates.length > 0) {
      dashboardApi.getProfitTrend(trendDays)
        .then(setTrend)
        .catch(() => {});
    }
  }, [trendDays]);

  if (loading) return <PageSkeleton type="dashboard" />;

  if (!data || data.positions.length === 0) return <EmptyGuide />;

  const trendOption = trend ? {
    tooltip: { trigger: 'axis' as const },
    xAxis: { type: 'category' as const, data: trend.dates },
    yAxis: { type: 'value' as const, axisLabel: { formatter: (v: number) => `${v.toFixed(0)}` } },
    series: [{
      type: 'bar',
      data: trend.profits,
      itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: (params: { value: number }) => params.value >= 0 ? '#F5222D' : '#52C41A',
      },
    }],
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
  } : {};

  return (
    <div className="fund-fade-in">
      {/* Asset Overview */}
      <Card className="fund-card-static" style={{ marginBottom: 16 }}>
        <Row gutter={24} align="middle">
          <Col span={8} className="fund-stat-divider">
            <Statistic
              title={
                <span className="fund-stat-label">
                  总资产{' '}
                  <span onClick={toggle} style={{ cursor: 'pointer', color: '#bfbfbf', marginLeft: 4 }}>
                    {visible ? <EyeOutlined /> : <EyeInvisibleOutlined />}
                  </span>
                </span>
              }
              value={mask(formatAmount(data.totalAsset))}
              prefix={visible ? '¥' : ''}
              valueStyle={{ fontSize: 32, fontWeight: 700, color: '#1677FF', fontVariantNumeric: 'tabular-nums' }}
            />
          </Col>
          <Col span={8} className="fund-stat-divider">
            <Statistic
              title={<span className="fund-stat-label">总收益</span>}
              value={mask(formatAmount(data.totalProfit))}
              prefix={visible ? '¥' : ''}
              valueStyle={{ color: getProfitColor(data.totalProfit), fontVariantNumeric: 'tabular-nums' }}
              suffix={visible ? <span style={{ fontSize: 14 }}>{formatPercent(data.totalProfitRate)}</span> : null}
            />
          </Col>
          <Col span={8}>
            <Statistic
              title={
                <span className="fund-stat-label">
                  {data.todayProfitIsEstimate ? (
                    <>
                      今日预估收益{' '}
                      <Tooltip title="今日实际净值尚未公布，当前为预估值">
                        <QuestionCircleOutlined style={{ color: '#8c8c8c', fontSize: 12 }} />
                      </Tooltip>
                    </>
                  ) : '今日收益'}
                </span>
              }
              value={formatAmount(data.todayProfitIsEstimate ? data.todayEstimateProfit : data.todayProfit)}
              prefix="¥"
              valueStyle={{ color: getProfitColor(data.todayProfitIsEstimate ? data.todayEstimateProfit : data.todayProfit), fontVariantNumeric: 'tabular-nums' }}
              suffix={data.todayProfitIsEstimate ? <span style={{ fontSize: 14 }}>{formatPercent(data.todayEstimateReturn)}</span> : null}
            />
          </Col>
        </Row>
      </Card>

      {/* AI智能分析区域 - 三列布局 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <MarketOverviewCard />
        </Col>
        <Col span={8}>
          <RiskWarningCard />
        </Col>
        <Col span={8}>
          <RebalanceTimingCard />
        </Col>
      </Row>

      <Row gutter={16}>
        {/* Position List */}
        <Col span={14}>
          <Card
            className="fund-card-static"
            title={<span style={{ fontWeight: 600 }}>持仓基金 <span style={{ fontSize: 13, color: '#8C8C8C', fontWeight: 400, marginLeft: 4 }}>{data.positions.length}只</span></span>}
            extra={
              <span
                style={{ cursor: 'pointer', color: '#1677FF', fontSize: 13, display: 'flex', alignItems: 'center', gap: 4 }}
                onClick={() => navigate('/portfolio/add')}
              >
                <PlusOutlined /> 添加
              </span>
            }
          >
            {data.positions.map((p) => (
              <div
                key={p.id}
                className="fund-list-item"
                onClick={() => navigate(`/fund/${p.fundCode}`)}
              >
                <div style={{ display: 'flex', alignItems: 'center' }}>
                  <div className="fund-type-bar" style={{ background: getFundTypeColor(p.fundType) }} />
                  <div>
                    <div className="fund-name">{p.fundName}</div>
                    <div className="fund-secondary" style={{ marginTop: 2 }}>{p.fundCode} · {p.accountName}</div>
                  </div>
                </div>
                <div style={{ textAlign: 'right', minWidth: 160 }}>
                  {/* 持有金额 - 隐私加密 */}
                  <div style={{ fontWeight: 600, fontSize: 15, fontVariantNumeric: 'tabular-nums' }}>
                    {visible ? `¥${formatAmount(p.marketValue)}` : '****'}
                  </div>
                  {/* 收益 - 隐私加密（包括涨幅） */}
                  <div style={{ fontSize: 12, marginTop: 2, display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 6 }}>
                    <span style={{ color: '#8c8c8c' }}>收益</span>
                    <span style={{ color: getProfitColor(p.profit), fontWeight: 500 }}>
                      {visible ? `¥${formatAmount(p.profit)}` : '****'}
                    </span>
                    {visible && <PriceChange value={p.profitRate} size="sm" />}
                  </div>
                  {/* 今日估值 - 不隐藏 */}
                  <div style={{ fontSize: 12, marginTop: 1, display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 6 }}>
                    <span style={{ color: '#8c8c8c' }}>今日估值</span>
                    <span style={{ color: getProfitColor(p.estimateProfit), fontWeight: 500 }}>
                      ¥{formatAmount(p.estimateProfit)}
                    </span>
                    <PriceChange value={p.estimateReturn} size="sm" />
                  </div>
                </div>
              </div>
            ))}
            <div
              style={{ textAlign: 'center', padding: '8px 0', cursor: 'pointer', color: '#8C8C8C', fontSize: 13 }}
              onClick={() => navigate('/portfolio')}
            >
              查看全部持仓 <RightOutlined style={{ fontSize: 10 }} />
            </div>
          </Card>
        </Col>

        {/* Profit Trend */}
        <Col span={10}>
          <Card
            className="fund-card-static"
            title={<span style={{ fontWeight: 600 }}>收益趋势</span>}
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
            {trend ? (
              <ReactECharts option={trendOption} theme="fundTheme" style={{ height: 250 }} />
            ) : (
              <PageSkeleton type="list" />
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Dashboard;
