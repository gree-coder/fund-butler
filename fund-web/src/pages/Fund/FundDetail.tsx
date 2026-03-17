import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, Col, Row, Tag, Table, Descriptions, Segmented, Spin, Button, Space, Tooltip, Dropdown, message } from 'antd';
import { StarOutlined, PlusOutlined, InfoCircleOutlined, DownOutlined, ReloadOutlined } from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import { fundApi, type FundDetail as FundDetailType, type EstimateItem } from '../../api/fund';
import { watchlistApi } from '../../api/watchlist';
import { formatAmount, formatPercent, formatNav, getProfitColor, formatFundType } from '../../utils/format';
import PriceChange from '../../components/PriceChange';

const PERIODS = [
  { label: '近1月', value: '1m' },
  { label: '近3月', value: '3m' },
  { label: '近6月', value: '6m' },
  { label: '近1年', value: '1y' },
  { label: '近3年', value: '3y' },
  { label: '全部', value: 'all' },
];

const FundDetail: React.FC = () => {
  const { code } = useParams<{ code: string }>();
  const [detail, setDetail] = useState<FundDetailType | null>(null);
  const [navDates, setNavDates] = useState<string[]>([]);
  const [navValues, setNavValues] = useState<number[]>([]);
  const [period, setPeriod] = useState('3m');
  const [loading, setLoading] = useState(true);
  const [estimateSources, setEstimateSources] = useState<EstimateItem[]>([]);
  const [activeEstimate, setActiveEstimate] = useState<EstimateItem | null>(null);
  const [actualSource, setActualSource] = useState<EstimateItem | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    if (!code) return;
    setLoading(true);
    fundApi.getDetail(code)
      .then(setDetail)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [code]);

  useEffect(() => {
    if (!code) return;
    fundApi.getNavHistory(code, period)
      .then((res) => {
        setNavDates(res.dates || []);
        setNavValues(res.navs || []);
      })
      .catch(() => {});
  }, [code, period]);

  useEffect(() => {
    if (!code) return;
    fundApi.getEstimates(code)
      .then((res) => {
        const sources = res.sources || [];
        setEstimateSources(sources);
        const actual = sources.find((s) => s.key === 'actual' && s.available);
        setActualSource(actual || null);
        // activeEstimate: 跳过actual（单独展示），优先smart > 第一个非actual可用源
        const smart = sources.find((s) => s.key === 'smart' && s.available);
        const firstNonActual = sources.find((s) => s.available && s.key !== 'actual');
        setActiveEstimate(smart || firstNonActual || null);
      })
      .catch(() => {});
  }, [code]);

  const handleRefresh = async () => {
    if (!code) return;
    setRefreshing(true);
    try {
      const result = await fundApi.refreshData(code);
      if (result.detail) setDetail(result.detail);
      if (result.estimates?.sources) {
        const sources = result.estimates.sources;
        setEstimateSources(sources);
        const actual = sources.find((s) => s.key === 'actual' && s.available);
        setActualSource(actual || null);
        const smart = sources.find((s) => s.key === 'smart' && s.available);
        const firstNonActual = sources.find((s) => s.available && s.key !== 'actual');
        setActiveEstimate(smart || firstNonActual || null);
      }
      message.success('数据已刷新');
    } catch {
      message.error('刷新失败');
    } finally {
      setRefreshing(false);
    }
  };

  if (loading || !detail) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;

  const chartOption = {
    tooltip: { trigger: 'axis' as const },
    xAxis: { type: 'category' as const, data: navDates, axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value' as const, scale: true, axisLabel: { formatter: (v: number) => v.toFixed(4) } },
    series: [{ type: 'line', data: navValues, smooth: true, lineStyle: { color: '#1677FF' }, areaStyle: { color: 'rgba(22,119,255,0.1)' } }],
    grid: { left: 60, right: 20, top: 20, bottom: 30 },
  };

  const perfColumns = [
    { title: '近1周', dataIndex: 'week1', render: (v: number) => <PriceChange value={v} /> },
    { title: '近1月', dataIndex: 'month1', render: (v: number) => <PriceChange value={v} /> },
    { title: '近3月', dataIndex: 'month3', render: (v: number) => <PriceChange value={v} /> },
    { title: '近6月', dataIndex: 'month6', render: (v: number) => <PriceChange value={v} /> },
    { title: '近1年', dataIndex: 'year1', render: (v: number) => <PriceChange value={v} /> },
    { title: '近3年', dataIndex: 'year3', render: (v: number) => <PriceChange value={v} /> },
    { title: '成立来', dataIndex: 'sinceEstablish', render: (v: number) => <PriceChange value={v} /> },
  ];

  const holdingColumns = [
    { title: '股票代码', dataIndex: 'stockCode' },
    { title: '股票名称', dataIndex: 'stockName' },
    { title: '占比(%)', dataIndex: 'ratio', render: (v: number) => `${v}%` },
    { title: '今日涨跌', dataIndex: 'changePercent', render: (v: number) => v != null ? <PriceChange value={v} /> : '--' },
  ];

  const industryOption = {
    tooltip: { trigger: 'item' as const },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: (detail.industryDist || []).map((d) => ({ name: d.industry, value: d.ratio })),
      label: { fontSize: 11 },
    }],
  };

  return (
    <div>
      {/* Header */}
      <Card style={{ marginBottom: 16 }}>
        <Row justify="space-between" align="middle">
          <Col>
            <h2 style={{ margin: 0 }}>{detail.name} <Tag>{formatFundType(detail.type)}</Tag></h2>
            <div style={{ color: '#999', marginTop: 4 }}>{detail.code} | {detail.company} | {detail.manager}</div>
          </Col>
          <Col>
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: 28, fontWeight: 700 }}>{formatNav(detail.latestNav)}</div>
              <div style={{ fontSize: 12, color: '#999' }}>
                最新净值 ({detail.latestNavDate})
                <div style={{ marginTop: 4 }}>
                  <div>
                    <Tag color="blue" style={{ fontSize: 11 }}>估值</Tag>{' '}
                    <span style={{ color: getProfitColor(activeEstimate?.estimateReturn ?? detail.estimateReturn) }}>
                      {formatNav(activeEstimate?.estimateNav ?? detail.estimateNav)} ({formatPercent(activeEstimate?.estimateReturn ?? detail.estimateReturn)})
                    </span>
                    {estimateSources.length > 0 ? (
                      <Dropdown
                        menu={{
                          items: estimateSources.filter((s) => s.key !== 'actual').map((s) => ({
                            key: s.key,
                            label: (
                              <div>
                                <div style={{ fontWeight: activeEstimate?.key === s.key ? 600 : 400 }}>
                                  {s.label} {!s.available && <Tag color="default" style={{ fontSize: 10 }}>不可用</Tag>}
                                </div>
                                {s.available && (
                                  <div style={{ fontSize: 11, color: '#666' }}>
                                    {formatNav(s.estimateNav)} ({formatPercent(s.estimateReturn)})
                                  </div>
                                )}
                                <div style={{ fontSize: 11, color: '#999' }}>{s.description}</div>
                              </div>
                            ),
                            disabled: !s.available,
                          })),
                          onClick: ({ key }) => {
                            const source = estimateSources.find((s) => s.key === key);
                            if (source) setActiveEstimate(source);
                          },
                        }}
                        trigger={['click']}
                      >
                        <span style={{ marginLeft: 4, color: '#1677ff', cursor: 'pointer', fontSize: 12 }}>
                          {activeEstimate?.label ?? '切换数据源'} <DownOutlined style={{ fontSize: 10 }} />
                        </span>
                      </Dropdown>
                    ) : detail.estimateSource ? (
                      <Tooltip title={`数据来源: ${detail.estimateSource}`}>
                        <InfoCircleOutlined style={{ marginLeft: 4, color: '#999', cursor: 'pointer' }} />
                      </Tooltip>
                    ) : null}
                  </div>
                  {actualSource && (
                    <div style={{ marginTop: 4 }}>
                      <Tag color="gold" style={{ fontSize: 11 }}>实际</Tag>{' '}
                      <span style={{ color: getProfitColor(actualSource.estimateReturn), fontWeight: 600 }}>
                        {formatNav(actualSource.estimateNav)} ({formatPercent(actualSource.estimateReturn)})
                      </span>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </Col>
        </Row>
        <Space style={{ marginTop: 12 }}>
          <Button icon={<ReloadOutlined />} loading={refreshing} onClick={handleRefresh}>刷新数据</Button>
          <Button icon={<StarOutlined />} onClick={() => {
            watchlistApi.add({ fundCode: detail.code }).then(() => message.success('已添加到自选')).catch(() => {});
          }}>加自选</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate(`/portfolio/add?fundCode=${detail.code}`)}>加持仓</Button>
        </Space>
      </Card>

      {/* NAV Chart */}
      <Card title="净值走势" extra={<Segmented size="small" options={PERIODS} value={period} onChange={(v) => setPeriod(v as string)} />} style={{ marginBottom: 16 }}>
        <ReactECharts option={chartOption} style={{ height: 300 }} />
      </Card>

      {/* Performance */}
      <Card title="历史业绩" style={{ marginBottom: 16 }}>
        <Table columns={perfColumns} dataSource={[detail.performance]} pagination={false} rowKey={() => 'perf'} size="small" />
      </Card>

      <Row gutter={16}>
        {/* Top Holdings */}
        <Col span={12}>
          <Card title="十大重仓股" style={{ marginBottom: 16 }}>
            <Table columns={holdingColumns} dataSource={detail.topHoldings || []} pagination={false} rowKey="stockCode" size="small" />
          </Card>
        </Col>
        {/* Industry Distribution + Sector Changes */}
        <Col span={12}>
          <Card title="行业分布" style={{ marginBottom: 16 }}>
            {detail.industryDist && detail.industryDist.length > 0
              ? <ReactECharts option={industryOption} style={{ height: 280 }} />
              : <div style={{ textAlign: 'center', color: '#999', padding: 40 }}>暂无数据</div>
            }
          </Card>
          {detail.sectorChanges && detail.sectorChanges.length > 0 && (
            <Card title="关联行业板块今日涨幅" size="small" style={{ marginBottom: 16 }}>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                {detail.sectorChanges.map((s) => (
                  <Tag
                    key={s.sectorName}
                    color={s.changePercent > 0 ? 'red' : s.changePercent < 0 ? 'green' : 'default'}
                    style={{ fontSize: 13, padding: '4px 8px' }}
                  >
                    {s.sectorName} <span style={{ fontWeight: 600 }}>{s.changePercent > 0 ? '+' : ''}{s.changePercent?.toFixed(2)}%</span>
                  </Tag>
                ))}
              </div>
            </Card>
          )}
        </Col>
      </Row>

      {/* Fund Info */}
      <Card title="基金信息">
        <Descriptions column={2} size="small">
          <Descriptions.Item label="基金代码">{detail.code}</Descriptions.Item>
          <Descriptions.Item label="基金类型">{formatFundType(detail.type)}</Descriptions.Item>
          <Descriptions.Item label="基金公司">{detail.company}</Descriptions.Item>
          <Descriptions.Item label="基金经理">{detail.manager}</Descriptions.Item>
          <Descriptions.Item label="成立日期">{detail.establishDate}</Descriptions.Item>
          <Descriptions.Item label="基金规模">{detail.scale ? `${formatAmount(detail.scale)}亿` : '--'}</Descriptions.Item>
          <Descriptions.Item label="风险等级">{['', '低', '中低', '中', '中高', '高'][detail.riskLevel] || '--'}</Descriptions.Item>
          <Descriptions.Item label="申购费">{detail.feeRate?.purchaseRate ?? '--'}</Descriptions.Item>
          <Descriptions.Item label="管理费">{detail.feeRate?.managementFee ?? '--'}</Descriptions.Item>
          <Descriptions.Item label="托管费">{detail.feeRate?.custodyFee ?? '--'}</Descriptions.Item>
        </Descriptions>
      </Card>
    </div>
  );
};

export default FundDetail;
