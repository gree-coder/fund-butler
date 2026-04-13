import React, { useEffect, useState } from 'react';
import { Card, Tag, Progress, Row, Col, Typography, Space } from 'antd';
import { WarningOutlined, SafetyOutlined, CheckCircleOutlined, InfoCircleOutlined, AlertOutlined, DashboardOutlined } from '@ant-design/icons';
import { dashboardApi, type RiskWarningData, type RiskItem } from '../api/dashboard';

const { Text } = Typography;

const RiskWarningCard: React.FC = () => {
  const [warning, setWarning] = useState<RiskWarningData | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    dashboardApi.getRiskWarning()
      .then((data) => {
        setWarning(data);
      })
      .catch(() => {
        // 静默失败
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  if (loading || !warning) {
    return null;
  }

  // 获取风险等级配置
  const getRiskLevelConfig = (level: string) => {
    switch (level) {
      case 'high':
        return { color: 'red', icon: <AlertOutlined />, text: '高风险' };
      case 'medium':
        return { color: 'orange', icon: <WarningOutlined />, text: '中风险' };
      case 'low':
        return { color: 'green', icon: <SafetyOutlined />, text: '低风险' };
      default:
        return { color: 'default', icon: <InfoCircleOutlined />, text: '未知' };
    }
  };

  // 获取单项风险等级颜色
  const getItemLevelColor = (level: string) => {
    switch (level) {
      case 'critical':
        return '#ff4d4f';
      case 'high':
        return '#ff7875';
      case 'medium':
        return '#ffa940';
      case 'low':
        return '#73d13d';
      default:
        return '#bfbfbf';
    }
  };

  // 获取单项风险等级标签
  const getItemLevelTag = (level: string) => {
    switch (level) {
      case 'critical':
        return <Tag color="error">严重</Tag>;
      case 'high':
        return <Tag color="warning">高</Tag>;
      case 'medium':
        return <Tag color="processing">中</Tag>;
      case 'low':
        return <Tag color="success">低</Tag>;
      default:
        return <Tag>未知</Tag>;
    }
  };

  const riskConfig = getRiskLevelConfig(warning.overallRiskLevel);
  const hasRisks = warning.risks && warning.risks.length > 0;

  return (
    <Card
      className="risk-warning-card"
      size="small"
      style={{
        borderRadius: 8,
        border: warning.overallRiskLevel === 'high' ? '1px solid #ff4d4f' :
                warning.overallRiskLevel === 'medium' ? '1px solid #ffa940' : '1px solid #52c41a',
        height: '100%'
      }}
      title={
        <Space align="center">
          <DashboardOutlined style={{ fontSize: 16, color: riskConfig.color }} />
          <span style={{ fontSize: 14, fontWeight: 600 }}>风险预警</span>
          <Tag color={riskConfig.color} icon={riskConfig.icon} style={{ fontSize: 11 }}>
            {riskConfig.text}
          </Tag>
        </Space>
      }
      extra={
        <Text type="secondary" style={{ fontSize: 11 }}>
          {warning.healthMetrics.totalPositions}只基金
        </Text>
      }
    >
      {/* 健康指标 - 四维度展示 */}
      <div style={{ marginBottom: 12 }}>
        <Row gutter={[8, 8]}>
          <Col span={8}>
            <div style={{ textAlign: 'center', padding: '8px 4px', background: '#f5f5f5', borderRadius: 6 }}>
              <div style={{ fontSize: 11, color: '#666', marginBottom: 4 }}>整体健康度</div>
              <div style={{
                fontSize: 20,
                fontWeight: 600,
                color: warning.healthMetrics.overallHealth >= 80 ? '#52c41a' :
                       warning.healthMetrics.overallHealth >= 60 ? '#faad14' : '#ff4d4f'
              }}>
                {warning.healthMetrics.overallHealth}
              </div>
            </div>
          </Col>
          <Col span={16}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <div>
                <div style={{ fontSize: 10, color: '#999', marginBottom: 1 }}>行业分散</div>
                <Progress
                  percent={warning.healthMetrics.industryDiversification}
                  size="small"
                  strokeColor={warning.healthMetrics.industryDiversification >= 80 ? '#52c41a' :
                              warning.healthMetrics.industryDiversification >= 60 ? '#faad14' : '#ff4d4f'}
                  format={(percent) => <span style={{ fontSize: 10 }}>{percent}</span>}
                />
              </div>
              <div>
                <div style={{ fontSize: 10, color: '#999', marginBottom: 1 }}>集中度</div>
                <Progress
                  percent={warning.healthMetrics.concentrationScore}
                  size="small"
                  strokeColor={warning.healthMetrics.concentrationScore >= 80 ? '#52c41a' :
                              warning.healthMetrics.concentrationScore >= 60 ? '#faad14' : '#ff4d4f'}
                  format={(percent) => <span style={{ fontSize: 10 }}>{percent}</span>}
                />
              </div>
              <div>
                <div style={{ fontSize: 10, color: '#999', marginBottom: 1 }}>风险平衡</div>
                <Progress
                  percent={warning.healthMetrics.riskBalanceScore}
                  size="small"
                  strokeColor={warning.healthMetrics.riskBalanceScore >= 80 ? '#52c41a' :
                              warning.healthMetrics.riskBalanceScore >= 60 ? '#faad14' : '#ff4d4f'}
                  format={(percent) => <span style={{ fontSize: 10 }}>{percent}</span>}
                />
              </div>
              <div>
                <div style={{ fontSize: 10, color: '#999', marginBottom: 1 }}>估值健康</div>
                <Progress
                  percent={warning.healthMetrics.valuationHealthScore}
                  size="small"
                  strokeColor={warning.healthMetrics.valuationHealthScore >= 80 ? '#52c41a' :
                              warning.healthMetrics.valuationHealthScore >= 60 ? '#faad14' : '#ff4d4f'}
                  format={(percent) => <span style={{ fontSize: 10 }}>{percent}</span>}
                />
              </div>
            </div>
          </Col>
        </Row>
      </div>

      {/* 风险状态 */}
      {hasRisks ? (
        <div style={{ marginBottom: 8 }}>
          <Space size={4}>
            <WarningOutlined style={{ color: '#faad14', fontSize: 12 }} />
            <Text strong style={{ fontSize: 12 }}>发现 {warning.risks.length} 项风险</Text>
          </Space>
          <div style={{ marginTop: 6 }}>
            {warning.risks.map((risk: RiskItem, idx: number) => (
              <div
                key={idx}
                style={{
                  padding: '6px 8px',
                  background: '#fff2f0',
                  borderRadius: 4,
                  marginBottom: 4,
                  borderLeft: `2px solid ${getItemLevelColor(risk.level)}`
                }}
              >
                <Space size={4}>
                  {getItemLevelTag(risk.level)}
                  <Text strong style={{ fontSize: 11 }}>{risk.title}</Text>
                </Space>
                <div style={{ fontSize: 10, color: '#666', marginTop: 2 }}>
                  {risk.suggestion}
                </div>
              </div>
            ))}
          </div>
        </div>
      ) : (
        <div style={{ 
          padding: '10px', 
          background: '#f6ffed', 
          borderRadius: 6,
          textAlign: 'center',
          marginBottom: 8
        }}>
          <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 16 }} />
          <div style={{ fontSize: 12, color: '#52c41a', marginTop: 4 }}>
            持仓结构良好
          </div>
        </div>
      )}

      {/* 优化建议 - 简化 */}
      {warning.suggestions && warning.suggestions.length > 0 && (
        <div style={{ padding: '8px', background: '#f6ffed', borderRadius: 6 }}>
          <Space size={4} style={{ marginBottom: 4 }}>
            <SafetyOutlined style={{ color: '#52c41a', fontSize: 12 }} />
            <Text strong style={{ fontSize: 11 }}>优化建议</Text>
          </Space>
          <div style={{ fontSize: 10, color: '#666' }}>
            {warning.suggestions[0].content}
          </div>
        </div>
      )}
    </Card>
  );
};

export default RiskWarningCard;
