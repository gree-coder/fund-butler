import { useEffect, useState } from 'react';
import { Card, Table, Tag, Progress, Tooltip, Empty, Spin, Statistic, Row, Col } from 'antd';
import { InfoCircleOutlined, RiseOutlined, FallOutlined, MinusOutlined } from '@ant-design/icons';
import { estimateAnalysisApi, type EstimateAnalysisData } from '../../api/estimateAnalysis';
import PriceChange from '../../components/PriceChange';

interface Props {
  fundCode: string;
}

export function EstimateAnalysisTab({ fundCode }: Props) {
  const [data, setData] = useState<EstimateAnalysisData | null>(null);
  const [loading, setLoading] = useState(true);

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
      title: '可信度',
      dataIndex: 'confidence',
      key: 'confidence',
      render: (confidence: number) => confidence !== undefined ? (
        <Tooltip title={`基于历史准确度计算: ${(confidence * 100).toFixed(0)}%`}>
          <Progress
            percent={Math.round(confidence * 100)}
            size="small"
            status={confidence > 0.7 ? 'success' : confidence > 0.4 ? 'normal' : 'exception'}
            style={{ width: 80 }}
          />
        </Tooltip>
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
      title: '评级',
      dataIndex: 'rating',
      key: 'rating',
      render: (rating: number) => '⭐'.repeat(rating),
    },
    {
      title: '平均误差',
      dataIndex: 'mae',
      key: 'mae',
      render: (mae: number) => `${mae?.toFixed(2) || '--'}%`,
    },
    {
      title: '命中率',
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
      title: '趋势',
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
          {type === 'ACTUAL' ? '实际净值' : '预测补偿'}
        </Tag>
      ),
    },
    {
      title: '补偿前',
      key: 'before',
      render: (_: unknown, record: { beforeNav?: number; beforeReturn?: number }) =>
        record.beforeNav ? (
          <div>
            <div>净值: {record.beforeNav.toFixed(4)}</div>
            <div><PriceChange value={record.beforeReturn || 0} /></div>
          </div>
        ) : (
          '--'
        ),
    },
    {
      title: '补偿后',
      key: 'after',
      render: (_: unknown, record: { afterNav?: number; afterReturn?: number }) => (
        <div>
          <div>净值: {record.afterNav?.toFixed(4) || '--'}</div>
          <div><PriceChange value={record.afterReturn || 0} /></div>
        </div>
      ),
    },
    {
      title: '数据来源',
      dataIndex: 'source',
      key: 'source',
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
      {/* 实际净值与智能预估 */}
      {currentEstimates.actualNav && (
        <Card style={{ marginBottom: 16 }}>
          <Row gutter={24}>
            <Col span={8}>
              <Statistic
                title={
                  <span>
                    今日实际净值
                    {currentEstimates.actualReturnDelayed && (
                      <Tooltip title="净值发布存在延迟">
                        <Tag color="warning" style={{ marginLeft: 8 }}>延迟</Tag>
                      </Tooltip>
                    )}
                  </span>
                }
                value={currentEstimates.actualNav}
                precision={4}
              />
            </Col>
            <Col span={8}>
              <Statistic
                title="实际涨幅"
                valueRender={() => (
                  <PriceChange value={currentEstimates.actualReturn || 0} />
                )}
              />
            </Col>
            <Col span={8}>
              <Statistic
                title="智能综合预估"
                value={currentEstimates.smartEstimate?.nav}
                precision={4}
                suffix={
                  currentEstimates.smartEstimate?.accuracyEnhanced && (
                    <Tooltip title="已应用历史准确度修正">
                      <Tag color="success" style={{ marginLeft: 8 }}>准确度增强</Tag>
                    </Tooltip>
                  )
                }
              />
            </Col>
          </Row>
          {currentEstimates.smartEstimate?.description && (
            <div style={{ marginTop: 8, color: '#666' }}>
              <InfoCircleOutlined style={{ marginRight: 4 }} />
              {currentEstimates.smartEstimate.description}
            </div>
          )}
        </Card>
      )}

      {/* 实时数据源对比 */}
      <Card
        title="实时数据源对比"
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
          <Table
            dataSource={compensationLogs}
            columns={compensationColumns}
            rowKey="date"
            pagination={false}
            size="small"
          />
        ) : (
          <Empty description="暂无补偿记录" />
        )}
      </Card>
    </div>
  );
}
