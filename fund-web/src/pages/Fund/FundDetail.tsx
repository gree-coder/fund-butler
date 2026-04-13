import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, Col, Row, Tag, Table, Descriptions, Segmented, Button, Space, Tooltip, Dropdown, message, Tabs } from 'antd';
import dayjs from 'dayjs';
import { StarOutlined, StarFilled, PlusOutlined, InfoCircleOutlined, DownOutlined, ReloadOutlined, RobotOutlined } from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import { fundApi, type FundDetail as FundDetailType, type EstimateItem } from '../../api/fund';
import { watchlistApi } from '../../api/watchlist';
import { formatAmount, formatPercent, formatNav, getProfitColor, formatFundType, FUND_TYPE_TAG_COLOR } from '../../utils/format';
import PriceChange from '../../components/PriceChange';
import PageSkeleton from '../../components/PageSkeleton';
import { EstimateAnalysisTab } from './EstimateAnalysisTab';
import DiagnosisTab from './DiagnosisTab';

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
  const [inWatchlist, setInWatchlist] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    if (!code) return;
    setLoading(true);
    fundApi.getDetail(code)
      .then(setDetail)
      .catch(() => {})
      .finally(() => setLoading(false));
    watchlistApi.checkExists([code])
      .then((existCodes) => setInWatchlist(existCodes.includes(code)))
      .catch(() => {});
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

  if (loading || !detail) return <PageSkeleton type="detail" />;

  const chartOption = {
    tooltip: { trigger: 'axis' as const },
    xAxis: { type: 'category' as const, data: navDates },
    yAxis: { type: 'value' as const, scale: true, axisLabel: { formatter: (v: number) => v.toFixed(4) } },
    series: [{
      type: 'line',
      data: navValues,
      smooth: true,
      lineStyle: { color: '#1677FF', width: 2 },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(22,119,255,0.2)' },
            { offset: 1, color: 'rgba(22,119,255,0.02)' },
          ],
        },
      },
      symbol: 'none',
    }],
    grid: { left: 60, right: 20, top: 20, bottom: 30 },
  };

  const perfColumns = [
    { title: '近1周', dataIndex: 'week1', render: (v: number) => <PriceChange value={v} showBg /> },
    { title: '近1月', dataIndex: 'month1', render: (v: number) => <PriceChange value={v} showBg /> },
    { title: '近3月', dataIndex: 'month3', render: (v: number) => <PriceChange value={v} showBg /> },
    { title: '近6月', dataIndex: 'month6', render: (v: number) => <PriceChange value={v} showBg /> },
    { title: '近1年', dataIndex: 'year1', render: (v: number) => <PriceChange value={v} showBg /> },
    { title: '近3年', dataIndex: 'year3', render: (v: number) => <PriceChange value={v} showBg /> },
    { title: '成立来', dataIndex: 'sinceEstablish', render: (v: number) => <PriceChange value={v} showBg /> },
  ];

  const holdingColumns = [
    { title: '股票代码', dataIndex: 'stockCode', render: (v: string) => <span className="fund-code" style={{ fontSize: 12 }}>{v}</span> },
    { title: '股票名称', dataIndex: 'stockName' },
    {
      title: '占比(%)', dataIndex: 'ratio', render: (v: number) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{ flex: 1, background: '#f0f0f0', borderRadius: 3, height: 8 }}>
            <div style={{ width: `${Math.min(v * 2, 100)}%`, background: '#1677FF', borderRadius: 3, height: '100%' }} />
          </div>
          <span style={{ width: 36, textAlign: 'right', fontSize: 12, whiteSpace: 'nowrap' }}>{v.toFixed(2)}%</span>
        </div>
      ),
    },
    { title: '今日涨跌', dataIndex: 'changePercent', render: (v: number) => v != null ? <PriceChange value={v} showBg size="sm" /> : '--' },
  ];

  const industryOption = {
    tooltip: { trigger: 'item' as const },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: (detail.industryDist || []).map((d) => ({ name: d.industry, value: d.ratio })),
      label: { fontSize: 11, formatter: '{b} {d}%' },
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
    }],
  };

  return (
    <div className="fund-fade-in">
      {/* Header */}
      <Card className="fund-card-static" style={{ marginBottom: 16 }}>
        <Row justify="space-between" align="top">
          <Col>
            <h2 style={{ margin: 0, fontSize: 22, fontWeight: 700 }}>
              {detail.name}{' '}
              <Tag color={FUND_TYPE_TAG_COLOR[detail.type] || 'default'}>{formatFundType(detail.type)}</Tag>
            </h2>
            <div className="fund-secondary" style={{ marginTop: 6 }}>
              <span className="fund-code" style={{ fontSize: 13 }}>{detail.code}</span>
              <span style={{ margin: '0 8px', color: '#d9d9d9' }}>|</span>
              {detail.company}
              <span style={{ margin: '0 8px', color: '#d9d9d9' }}>|</span>
              {detail.manager}
            </div>
          </Col>
          <Col>
            <Space>
              <Button icon={<ReloadOutlined />} loading={refreshing} onClick={handleRefresh}>刷新</Button>
              <Button
                type={inWatchlist ? 'text' : 'default'}
                icon={inWatchlist ? <StarFilled style={{ color: '#faad14' }} /> : <StarOutlined />}
                style={inWatchlist ? { color: '#faad14' } : undefined}
                onClick={() => {
                  if (inWatchlist) {
                    watchlistApi.removeByCode(detail.code).then(() => {
                      message.success('已取消自选');
                      setInWatchlist(false);
                    }).catch(() => {});
                  } else {
                    watchlistApi.add({ fundCode: detail.code }).then(() => {
                      message.success('已添加到自选');
                      setInWatchlist(true);
                    }).catch(() => {});
                  }
                }}
              >{inWatchlist ? '已自选' : '自选'}</Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate(`/portfolio/add?fundCode=${detail.code}`)}>加持仓</Button>
            </Space>
          </Col>
        </Row>

        {/* NAV Info Grid */}
        <div className="fund-nav-grid">
          {/* 最新净值 - 根据 QDII 延迟情况动态显示 */}
          <div>
            <div className="fund-stat-label">
              {actualSource?.delayed ? (
                <>
                  <Tag color="gold" style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}>T+1</Tag>
                  最新净值
                </>
              ) : '最新净值'}
            </div>
            <div style={{ fontSize: 28, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
              {formatNav(actualSource?.estimateNav ?? detail.latestNav)}
            </div>
            <div className="fund-secondary">
              {actualSource?.delayed ? (
                <Tooltip title="QDII基金净值延迟一天公布，显示的是前一交易日数据">
                  <span style={{ color: '#faad14' }}>{actualSource.delayedDate} (延迟)</span>
                </Tooltip>
              ) : detail.latestNavDate}
            </div>
            {actualSource && (
              <div style={{ marginTop: 4 }}>
                <PriceChange value={actualSource.estimateReturn} showBg />
              </div>
            )}
          </div>
          <div>
            <div className="fund-stat-label">
              估值预测
              {estimateSources.length > 0 ? (
                <Dropdown
                  menu={{
                    items: estimateSources.filter((s) => s.key !== 'actual').map((s) => ({
                      key: s.key,
                      label: (
                        <div style={{ padding: '2px 0' }}>
                          <div style={{ fontWeight: activeEstimate?.key === s.key ? 600 : 400, display: 'flex', alignItems: 'center', gap: 6 }}>
                            {activeEstimate?.key === s.key && <span style={{ color: '#1677FF' }}>✓</span>}
                            {s.label}
                            {!s.available && <Tag color="default" style={{ fontSize: 10 }}>不可用</Tag>}
                          </div>
                          {s.available && (
                            <div style={{ fontSize: 11, color: '#666', marginTop: 2 }}>
                              {formatNav(s.estimateNav)} ({formatPercent(s.estimateReturn)})
                            </div>
                          )}
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
                  <span style={{ marginLeft: 6, color: '#1677ff', cursor: 'pointer', fontSize: 12 }}>
                    {activeEstimate?.label ?? '切换源'} <DownOutlined style={{ fontSize: 10 }} />
                  </span>
                </Dropdown>
              ) : detail.estimateSource ? (
                <Tooltip title={`数据来源: ${detail.estimateSource}`}>
                  <InfoCircleOutlined style={{ marginLeft: 4, color: '#bfbfbf', cursor: 'pointer' }} />
                </Tooltip>
              ) : null}
            </div>
            <div style={{ fontSize: 24, fontWeight: 600, color: getProfitColor(activeEstimate?.estimateReturn ?? detail.estimateReturn), fontVariantNumeric: 'tabular-nums' }}>
              {formatNav(activeEstimate?.estimateNav ?? detail.estimateNav)}
            </div>
            <PriceChange value={activeEstimate?.estimateReturn ?? detail.estimateReturn} showBg />
            <div className="fund-secondary" style={{ marginTop: 4 }}>
              今日预估 ({dayjs().format('MM月DD日')})
            </div>
          </div>
        </div>
      </Card>

      {/* Tabs */}
      <Tabs
        defaultActiveKey="overview"
        items={[
          {
            key: 'overview',
            label: '概览',
            children: (
              <>
                {/* NAV Chart */}
                <Card className="fund-card-static" title={<span style={{ fontWeight: 600 }}>净值走势</span>} extra={<Segmented size="small" options={PERIODS} value={period} onChange={(v) => setPeriod(v as string)} />} style={{ marginBottom: 16 }}>
                  <ReactECharts option={chartOption} theme="fundTheme" style={{ height: 350 }} notMerge={true} key={period} />
                </Card>

                {/* Performance */}
                <Card className="fund-card-static" title={<span style={{ fontWeight: 600 }}>历史业绩</span>} style={{ marginBottom: 16 }}>
                  <Table columns={perfColumns} dataSource={[detail.performance]} pagination={false} rowKey={() => 'perf'} size="small" />
                </Card>

                <Row gutter={16}>
                  <Col span={12}>
                    <Card
                      className="fund-card-static"
                      title={
                        <Space>
                          <span style={{ fontWeight: 600 }}>十大重仓股</span>
                          {detail.holdingsDate && (
                            <Tooltip title={`持仓数据披露日期：${detail.holdingsDate}，季报披露存在滞后性，请注意时效性`}>
                              <Tag color="orange" style={{ marginLeft: 8, fontWeight: 400 }}>
                                数据截至 {detail.holdingsDate}
                              </Tag>
                            </Tooltip>
                          )}
                        </Space>
                      }
                      style={{ marginBottom: 16 }}
                    >
                      <Table columns={holdingColumns} dataSource={detail.topHoldings || []} pagination={false} rowKey="stockCode" size="small" />
                    </Card>
                  </Col>
                  <Col span={12}>
                    <Card className="fund-card-static" title={<span style={{ fontWeight: 600 }}>行业分布</span>} style={{ marginBottom: 16 }}>
                      {detail.industryDist && detail.industryDist.length > 0
                        ? <ReactECharts option={industryOption} theme="fundTheme" style={{ height: 280 }} />
                        : <div style={{ textAlign: 'center', color: '#bfbfbf', padding: 40 }}>暂无数据</div>
                      }
                    </Card>
                    {detail.sectorChanges && detail.sectorChanges.length > 0 && (
                      <Card className="fund-card-static" title={<span style={{ fontWeight: 600 }}>关联板块今日涨幅</span>} size="small" style={{ marginBottom: 16 }}>
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                          {detail.sectorChanges.map((s) => (
                            <div
                              key={s.sectorName}
                              className="fund-sector-tag"
                              style={{
                                background: s.changePercent > 0 ? '#FFF1F0' : s.changePercent < 0 ? '#F6FFED' : '#FAFAFA',
                                color: s.changePercent > 0 ? '#F5222D' : s.changePercent < 0 ? '#52C41A' : '#8C8C8C',
                              }}
                            >
                              {s.sectorName}&nbsp;<span style={{ fontWeight: 600 }}>{s.changePercent > 0 ? '+' : ''}{s.changePercent?.toFixed(2)}%</span>
                            </div>
                          ))}
                        </div>
                      </Card>
                    )}
                  </Col>
                </Row>

                {/* Fund Info */}
                <Card className="fund-card-static" title={<span style={{ fontWeight: 600 }}>基金信息</span>}>
                  <Descriptions column={2} size="small" bordered>
                    <Descriptions.Item label="基金代码"><span className="fund-code" style={{ fontSize: 13 }}>{detail.code}</span></Descriptions.Item>
                    <Descriptions.Item label="基金类型"><Tag color={FUND_TYPE_TAG_COLOR[detail.type] || 'default'}>{formatFundType(detail.type)}</Tag></Descriptions.Item>
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
              </>
            ),
          },
          {
            key: 'estimate-analysis',
            label: '数据源分析',
            children: <EstimateAnalysisTab fundCode={code!} />,
          },
          {
            key: 'ai-diagnosis',
            label: (
              <span>
                <RobotOutlined style={{ marginRight: 4 }} />
                基金诊断
              </span>
            ),
            children: <DiagnosisTab fundCode={code!} />,
          },
        ]}
      />
    </div>
  );
};

export default FundDetail;
