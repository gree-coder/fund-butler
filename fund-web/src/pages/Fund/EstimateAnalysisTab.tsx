import { useEffect, useState } from 'react';
import { Card, Table, Tag, Progress, Tooltip, Empty, Spin, Pagination } from 'antd';
import { InfoCircleOutlined, RiseOutlined, FallOutlined, MinusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { estimateAnalysisApi, type EstimateAnalysisData } from '../../api/estimateAnalysis';
import PriceChange from '../../components/PriceChange';

interface Props {
  fundCode: string;
}

/**
 * 格式化补偿时间为用户友好的格式
 * - 今天：今天 18:30
 * - 昨天：昨天 20:00
 * - 今年：04-01 20:00
 * - 去年：2025-04-01 20:00
 */
function formatCompensatedAt(time: string | undefined): string {
  if (!time) return '--';
  
  const m = dayjs(time);
  const now = dayjs();
  
  if (m.isSame(now, 'day')) {
    return `今天 ${m.format('HH:mm')}`;
  }
  if (m.isSame(now.subtract(1, 'day'), 'day')) {
    return `昨天 ${m.format('HH:mm')}`;
  }
  if (m.isSame(now, 'year')) {
    return m.format('MM-DD HH:mm');
  }
  return m.format('YYYY-MM-DD HH:mm');
}

export function EstimateAnalysisTab({ fundCode }: Props) {
  const [data, setData] = useState<EstimateAnalysisData | null>(null);
  const [loading, setLoading] = useState(true);
  // 补偿记录分页状态
  const [compensationPage, setCompensationPage] = useState(1);
  const [compensationPageSize, setCompensationPageSize] = useState(10);

  useEffect(() => {
    const loadData = async () => {
      setLoading(true);
      try {
        const result = await estimateAnalysisApi.getAnalysis(fundCode);
        setData(result);
      } catch (error) {
        console.error('加载数据源分析失败:', error);
      } finally {
        setLoading(false);
      }
    };
    loadData();
  }, [fundCode]);

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '40px' }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!data) {
    return <Empty description="暂无数据" />;
  }

  const { currentEstimates, accuracyStats, compensationLogs } = data;

  // 数据源对比表格列
  const sourceColumns = [
    {
      title: '数据源',
      dataIndex: 'label',
      key: 'label',
    },
    {
      title: '预估净值',
      dataIndex: 'estimateNav',
      key: 'estimateNav',
      render: (val: number) => val?.toFixed(4) || '--',
    },
    {
      title: '预估涨幅',
      dataIndex: 'estimateReturn',
      key: 'estimateReturn',
      render: (val: number) => val !== undefined ? <PriceChange value={val} /> : '--',
    },
    {
      title: '状态',
      dataIndex: 'available',
      key: 'available',
      render: (available: boolean) => (
        <Tag color={available ? 'success' : 'error'}>
          {available ? '可用' : '不可用'}
        </Tag>
      ),
    },
    {
      title: '当前权重',
      dataIndex: 'weight',
      key: 'weight',
      render: (weight: number) => weight !== undefined ? (
        <Progress percent={Math.round(weight * 100)} size="small" style={{ width: 80 }} />
      ) : '--',
    },
    {
      title: (
        <span>
          可信度
          <Tooltip title="基于历史平均误差(MAE)计算，公式: 1/(1+MAE)。MAE越小可信度越高，MAE=0时可信度100%，MAE=0.5%时可信度67%">
            <InfoCircleOutlined style={{ marginLeft: 4, color: '#999', fontSize: 12 }} />
          </Tooltip>
        </span>
      ),
      dataIndex: 'confidence',
      key: 'confidence',
      render: (confidence: number) => confidence !== undefined ? (
        <Progress
          percent={Math.round(confidence * 100)}
          size="small"
          status={confidence > 0.7 ? 'success' : confidence > 0.4 ? 'normal' : 'exception'}
          style={{ width: 80 }}
        />
      ) : '--',
    },
    {
      title: '说明',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
  ];

  // 准确度统计表格列
  const accuracyColumns = [
    {
      title: '数据源',
      dataIndex: 'label',
      key: 'label',
    },
    {
      title: (
        <span>
          评级
          <Tooltip title="基于平均误差(MAE)的星级评分：MAE<0.1%=5星，<0.2%=4星，<0.4%=3星，<0.7%=2星，≥0.7%=1星">
            <InfoCircleOutlined style={{ marginLeft: 4, color: '#999', fontSize: 12 }} />
          </Tooltip>
        </span>
      ),
      dataIndex: 'rating',
      key: 'rating',
      render: (rating: number) => '⭐'.repeat(rating),
    },
    {
      title: (
        <span>
          平均误差
          <Tooltip title="Mean Absolute Error (MAE)：历史预测误差绝对值的平均值，越小越好">
            <InfoCircleOutlined style={{ marginLeft: 4, color: '#999', fontSize: 12 }} />
          </Tooltip>
        </span>
      ),
      dataIndex: 'mae',
      key: 'mae',
      render: (mae: number) => `${mae?.toFixed(2) || '--'}%`,
    },
    {
      title: (
        <span>
          命中率
          <Tooltip title="预测误差≤0.5%的次数占总预测次数的比例。误差在0.5%以内视为预测命中">
            <InfoCircleOutlined style={{ marginLeft: 4, color: '#999', fontSize: 12 }} />
          </Tooltip>
        </span>
      ),
      dataIndex: 'hitRate',
      key: 'hitRate',
      render: (rate: number) => `${rate?.toFixed(1) || '--'}%`,
    },
    {
      title: '预测次数',
      dataIndex: 'predictionCount',
      key: 'predictionCount',
    },
    {
      title: (
        <span>
          趋势
          <Tooltip title="对比最近7天与前7天的平均误差变化：误差减小表示准确度提升，误差增大表示准确度下降">
            <InfoCircleOutlined style={{ marginLeft: 4, color: '#999', fontSize: 12 }} />
          </Tooltip>
        </span>
      ),
      dataIndex: 'trend',
      key: 'trend',
      render: (trend: string) => {
        const icons = {
          improving: <RiseOutlined style={{ color: '#52c41a' }} />,
          stable: <MinusOutlined style={{ color: '#faad14' }} />,
          declining: <FallOutlined style={{ color: '#f5222d' }} />,
        };
        const labels = {
          improving: '提升',
          stable: '稳定',
          declining: '下降',
        };
        return (
          <Tag icon={icons[trend as keyof typeof icons]} color={trend === 'improving' ? 'success' : trend === 'declining' ? 'error' : 'warning'}>
            {labels[trend as keyof typeof labels]}
          </Tag>
        );
      },
    },
  ];

  // 补偿记录表格列
  const compensationColumns = [
    {
      title: '日期',
      dataIndex: 'date',
      key: 'date',
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => (
        <Tag color={type === 'ACTUAL' ? 'blue' : 'purple'}>
          {type === 'ACTUAL' ? '实际净值' : '预测数据'}
        </Tag>
      ),
    },
    {
      title: '补偿前',
      key: 'before',
      render: (_: unknown, record: { beforeNav?: number; beforeReturn?: number }) =>
        record.beforeNav != null ? (
          <div>
            <div>净值: {record.beforeNav.toFixed(4)}</div>
            <div><PriceChange value={record.beforeReturn ?? 0} /></div>
          </div>
        ) : (
          '--'
        ),
    },
    {
      title: '补偿后',
      key: 'after',
      render: (_: unknown, record: { afterNav?: number; afterReturn?: number }) =>
        record.afterNav != null ? (
          <div>
            <div>净值: {record.afterNav.toFixed(4)}</div>
            <div><PriceChange value={record.afterReturn ?? 0} /></div>
          </div>
        ) : (
          '--'
        ),
    },
    {
      title: '数据来源',
      dataIndex: 'source',
      key: 'source',
    },
    {
      title: '补偿时间',
      dataIndex: 'compensatedAt',
      key: 'compensatedAt',
      render: (time: string) => formatCompensatedAt(time),
    },
    {
      title: '说明',
      dataIndex: 'reason',
      key: 'reason',
      ellipsis: true,
    },
  ];

  return (
    <div style={{ padding: '16px 0' }}>
      {/* 实时数据源对比 */}
      <Card
        title={
          <span>
            实时数据源对比
            {currentEstimates.snapshotTime && (
              <span style={{ fontSize: 14, fontWeight: 'normal', color: '#666', marginLeft: 8 }}>
                ({dayjs(currentEstimates.snapshotTime).format('MM月DD日 HH:mm')} 快照)
              </span>
            )}
          </span>
        }
        style={{ marginBottom: 16 }}
        extra={
          <Tooltip title="各数据源实时估值及当前权重">
            <InfoCircleOutlined />
          </Tooltip>
        }
      >
        <Table
          dataSource={currentEstimates.sources}
          columns={sourceColumns}
          rowKey="key"
          pagination={false}
          size="small"
        />
        {/* 权重变化说明 */}
        {currentEstimates.smartEstimate?.accuracyEnhanced && 
         currentEstimates.smartEstimate.baseWeights && 
         currentEstimates.smartEstimate.weights && (
          <div style={{ marginTop: 12, padding: '8px 12px', background: '#fafafa', borderRadius: 4 }}>
            <div style={{ marginBottom: 4, fontWeight: 500 }}>
              {currentEstimates.smartEstimate.scenario}场景权重变化：
            </div>
            <div style={{ fontSize: 12, color: '#666' }}>
              {Object.keys(currentEstimates.smartEstimate.weights).map(key => {
                const label = key === 'eastmoney' ? '天天基金' : 
                              key === 'sina' ? '新浪财经' : 
                              key === 'stock' ? '重仓股' : key;
                const base = (currentEstimates.smartEstimate.baseWeights?.[key] || 0) * 100;
                const final = (currentEstimates.smartEstimate.weights?.[key] || 0) * 100;
                const diff = final - base;
                const diffStr = diff > 0 ? `+${diff.toFixed(1)}` : diff.toFixed(1);
                // 权重上涨用红色，下跌用绿色（与基金涨跌颜色风格一致）
                const diffColor = diff > 0 ? '#f5222d' : diff < 0 ? '#52c41a' : '#666';
                return (
                  <span key={key} style={{ marginRight: 16 }}>
                    {label}: {base.toFixed(1)}% → <span style={{ color: diffColor, fontWeight: 500 }}>{final.toFixed(1)}%</span>
                    <span style={{ color: diffColor, fontSize: 11 }}>({diffStr}%)</span>
                  </span>
                );
              })}
            </div>
          </div>
        )}
        {!currentEstimates.smartEstimate?.accuracyEnhanced && 
         currentEstimates.smartEstimate?.baseWeights && (
          <div style={{ marginTop: 12, padding: '8px 12px', background: '#fafafa', borderRadius: 4 }}>
            <div style={{ marginBottom: 4, fontWeight: 500 }}>
              {currentEstimates.smartEstimate.scenario}场景基础权重：
            </div>
            <div style={{ fontSize: 12, color: '#666' }}>
              {Object.keys(currentEstimates.smartEstimate.baseWeights).map(key => {
                const label = key === 'eastmoney' ? '天天基金' : 
                              key === 'sina' ? '新浪财经' : 
                              key === 'stock' ? '重仓股' : key;
                const base = (currentEstimates.smartEstimate.baseWeights?.[key] || 0) * 100;
                return (
                  <span key={key} style={{ marginRight: 16 }}>
                    {label}: {base.toFixed(1)}%
                  </span>
                );
              })}
            </div>
          </div>
        )}
      </Card>

      {/* 准确度统计 */}
      <Card
        title={`数据源准确度统计 (最近${accuracyStats.period})`}
        style={{ marginBottom: 16 }}
        extra={
          <Tooltip title="基于历史预测数据计算的平均误差和命中率">
            <InfoCircleOutlined />
          </Tooltip>
        }
      >
        {accuracyStats.sources.length > 0 ? (
          <Table
            dataSource={accuracyStats.sources}
            columns={accuracyColumns}
            rowKey="key"
            pagination={false}
            size="small"
          />
        ) : (
          <Empty description="暂无足够的历史数据" />
        )}
      </Card>

      {/* 数据补偿记录 */}
      <Card
        title="数据补偿记录 (最近7天)"
        extra={
          <Tooltip title="显示净值数据的补偿历史，区分预测数据补偿和实际净值发布">
            <InfoCircleOutlined />
          </Tooltip>
        }
      >
        {compensationLogs.length > 0 ? (
          <>
            <Table
              dataSource={compensationLogs.slice(
                (compensationPage - 1) * compensationPageSize,
                compensationPage * compensationPageSize
              )}
              columns={compensationColumns}
              rowKey="date"
              pagination={false}
              size="small"
            />
            <div style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end' }}>
              <Pagination
                current={compensationPage}
                pageSize={compensationPageSize}
                total={compensationLogs.length}
                showSizeChanger
                pageSizeOptions={['10', '20']}
                onChange={(page, pageSize) => {
                  setCompensationPage(page);
                  if (pageSize) setCompensationPageSize(pageSize);
                }}
                size="small"
              />
            </div>
          </>
        ) : (
          <Empty description="暂无补偿记录" />
        )}
      </Card>
    </div>
  );
}
