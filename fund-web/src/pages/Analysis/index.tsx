import React, { useState } from 'react';
import { Card, Row, Col, Statistic, Segmented, Empty, Tooltip } from 'antd';
import { ArrowUpOutlined, ArrowDownOutlined, InfoCircleOutlined } from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import { useQuery } from '@tanstack/react-query';
import { dashboardApi } from '../../api/dashboard';
import { formatAmount, getProfitColor } from '../../utils/format';
import PageSkeleton from '../../components/PageSkeleton';

const ProfitAnalysis: React.FC = () => {
  const [days, setDays] = useState(30);
  
  const { data, isLoading } = useQuery({
    queryKey: ['profitAnalysis', days],
    queryFn: () => dashboardApi.getProfitAnalysis(days),
    staleTime: 5 * 60 * 1000,
  });

  if (isLoading) return <PageSkeleton type="dashboard" />;
  if (!data || !data.dates || data.dates.length === 0) {
    return (
      <Card>
        <Empty description="暂无收益数据，请先同步历史净值" />
      </Card>
    );
  }

  // 收益曲线图表配置
  const profitChartOption = {
    tooltip: {
      trigger: 'axis' as const,
      axisPointer: { type: 'cross' as const },
    },
    legend: {
      data: ['累计收益', '市值'],
      bottom: 0,
    },
    grid: { left: 60, right: 60, top: 20, bottom: 40 },
    xAxis: {
      type: 'category' as const,
      data: data.dates,
      axisLabel: { fontSize: 10, rotate: 45 },
    },
    yAxis: [
      {
        type: 'value' as const,
        name: '收益(元)',
        position: 'left' as const,
        axisLabel: { formatter: (v: number) => `¥${v.toFixed(0)}` },
      },
      {
        type: 'value' as const,
        name: '市值(元)',
        position: 'right' as const,
        axisLabel: { formatter: (v: number) => `¥${v.toFixed(0)}` },
      },
    ],
    series: [
      {
        name: '累计收益',
        type: 'line' as const,
        data: data.cumulativeProfits,
        smooth: true,
        lineStyle: { width: 2 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(245,34,45,0.3)' },
              { offset: 1, color: 'rgba(245,34,45,0.02)' },
            ],
          },
        },
        itemStyle: { color: '#F5222D' },
      },
      {
        name: '市值',
        type: 'line' as const,
        yAxisIndex: 1,
        data: data.marketValues,
        smooth: true,
        lineStyle: { width: 2, type: 'dashed' },
        itemStyle: { color: '#1677FF' },
      },
    ],
  };

  // 回撤图表配置
  const drawdownChartOption = {
    tooltip: {
      trigger: 'axis' as const,
      formatter: (params: { name: string; value: number }[]) => {
        const val = params[0]?.value ?? 0;
        return `${params[0]?.name}<br/>回撤: ${val.toFixed(2)}%`;
      },
    },
    grid: { left: 60, right: 20, top: 20, bottom: 40 },
    xAxis: {
      type: 'category' as const,
      data: data.dates,
      axisLabel: { fontSize: 10, rotate: 45 },
    },
    yAxis: {
      type: 'value' as const,
      name: '回撤(%)',
      axisLabel: { formatter: '{value}%' },
    },
    series: [
      {
        type: 'line' as const,
        data: data.drawdown.drawdownCurve,
        smooth: true,
        lineStyle: { color: '#FA8C16', width: 2 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(250,140,22,0.3)' },
              { offset: 1, color: 'rgba(250,140,22,0.02)' },
            ],
          },
        },
        itemStyle: { color: '#FA8C16' },
        markPoint: data.drawdown.maxDrawdown > 0 ? {
          data: [
            {
              name: '最大回撤',
              value: `${data.drawdown.maxDrawdown.toFixed(2)}%`,
              xAxis: data.drawdown.endDate,
              yAxis: -data.drawdown.maxDrawdown,
              itemStyle: { color: '#F5222D' },
            },
          ],
        } : undefined,
      },
    ],
  };

  // 每日收益柱状图
  const dailyProfitChartOption = {
    tooltip: { trigger: 'axis' as const },
    grid: { left: 60, right: 20, top: 20, bottom: 40 },
    xAxis: {
      type: 'category' as const,
      data: data.dates,
      axisLabel: { fontSize: 10, rotate: 45 },
    },
    yAxis: {
      type: 'value' as const,
      axisLabel: { formatter: '¥{value}' },
    },
    series: [{
      type: 'bar' as const,
      data: data.dailyProfits,
      itemStyle: {
        borderRadius: [2, 2, 0, 0],
        color: (params: { value: number }) => params.value >= 0 ? '#F5222D' : '#52C41A',
      },
    }],
  };

  const metrics = data.metrics;

  return (
    <div className="fund-fade-in">
      {/* 统计指标卡片 */}
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={4}>
            <Statistic
              title="总收益率"
              value={metrics.totalReturn}
              precision={2}
              suffix="%"
              valueStyle={{ color: getProfitColor(metrics.totalReturn), fontSize: 20 }}
            />
          </Col>
          <Col span={4}>
            <Statistic
              title="年化收益率"
              value={metrics.annualizedReturn}
              precision={2}
              suffix="%"
              valueStyle={{ color: getProfitColor(metrics.annualizedReturn), fontSize: 20 }}
            />
          </Col>
          <Col span={4}>
            <Statistic
              title={
                <span>
                  最大回撤{' '}
                  <Tooltip title="从峰值到谷底的最大跌幅">
                    <InfoCircleOutlined style={{ color: '#8c8c8c', fontSize: 12 }} />
                  </Tooltip>
                </span>
              }
              value={data.drawdown.maxDrawdown}
              precision={2}
              suffix="%"
              valueStyle={{ color: '#52C41A', fontSize: 20 }}
            />
          </Col>
          <Col span={4}>
            <Statistic
              title={
                <span>
                  夏普比率{' '}
                  <Tooltip title="(年化收益-无风险利率)/波动率，无风险利率按3%计算">
                    <InfoCircleOutlined style={{ color: '#8c8c8c', fontSize: 12 }} />
                  </Tooltip>
                </span>
              }
              value={metrics.sharpeRatio}
              precision={2}
              valueStyle={{ fontSize: 20 }}
            />
          </Col>
          <Col span={4}>
            <Statistic
              title="胜率"
              value={metrics.winRate}
              precision={1}
              suffix="%"
              valueStyle={{ fontSize: 20 }}
            />
          </Col>
          <Col span={4}>
            <Statistic
              title="波动率"
              value={metrics.volatility}
              precision={2}
              suffix="%"
              valueStyle={{ fontSize: 20 }}
            />
          </Col>
        </Row>
        {data.drawdown.startDate && (
          <Row style={{ marginTop: 16 }}>
            <Col>
              <span style={{ color: '#8c8c8c', fontSize: 12 }}>
                最大回撤区间: {data.drawdown.startDate} ~ {data.drawdown.endDate}，
                持续 {data.drawdown.duration} 天，
                金额 ¥{formatAmount(data.drawdown.maxDrawdownAmount)}
              </span>
            </Col>
          </Row>
        )}
      </Card>

      {/* 收益曲线 */}
      <Card
        title={<span style={{ fontWeight: 600 }}>收益曲线</span>}
        extra={
          <Segmented
            size="small"
            options={[
              { label: '近30天', value: 30 },
              { label: '近60天', value: 60 },
              { label: '近90天', value: 90 },
            ]}
            value={days}
            onChange={(v) => setDays(v as number)}
          />
        }
        style={{ marginBottom: 16 }}
      >
        <ReactECharts option={profitChartOption} theme="fundTheme" style={{ height: 300 }} />
      </Card>

      <Row gutter={16}>
        {/* 每日收益 */}
        <Col span={12}>
          <Card title={<span style={{ fontWeight: 600 }}>每日收益</span>}>
            <ReactECharts option={dailyProfitChartOption} theme="fundTheme" style={{ height: 280 }} />
          </Card>
        </Col>

        {/* 回撤分析 */}
        <Col span={12}>
          <Card title={<span style={{ fontWeight: 600 }}>回撤分析</span>}>
            <ReactECharts option={drawdownChartOption} theme="fundTheme" style={{ height: 280 }} />
          </Card>
        </Col>
      </Row>

      {/* 盈亏统计 */}
      <Card style={{ marginTop: 16 }}>
        <Row gutter={16}>
          <Col span={8}>
            <Statistic
              title="盈利天数"
              value={metrics.profitDays}
              prefix={<ArrowUpOutlined />}
              valueStyle={{ color: '#F5222D' }}
            />
          </Col>
          <Col span={8}>
            <Statistic
              title="亏损天数"
              value={metrics.lossDays}
              prefix={<ArrowDownOutlined />}
              valueStyle={{ color: '#52C41A' }}
            />
          </Col>
          <Col span={8}>
            <Statistic
              title="总交易日"
              value={metrics.profitDays + metrics.lossDays}
            />
          </Col>
        </Row>
      </Card>
    </div>
  );
};

export default ProfitAnalysis;
