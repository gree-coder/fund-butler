import React, { useEffect, useState } from 'react';
import { Card, Spin, Alert, Tag, Progress, Row, Col, Statistic, List, Typography, Space, Divider, Badge } from 'antd';
import { RobotOutlined, WarningOutlined, CheckCircleOutlined, InfoCircleOutlined, StarFilled, StarOutlined } from '@ant-design/icons';
import { fundApi, type AiFundDiagnosis } from '../../api/fund';


const { Title, Text, Paragraph } = Typography;

interface AiDiagnosisTabProps {
  fundCode: string;
}

const AiDiagnosisTab: React.FC<AiDiagnosisTabProps> = ({ fundCode }) => {
  const [diagnosis, setDiagnosis] = useState<AiFundDiagnosis | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    fundApi.getAiDiagnosis(fundCode)
      .then((data) => {
        setDiagnosis(data);
      })
      .catch((err) => {
        setError(err.message || '获取AI诊断报告失败');
      })
      .finally(() => {
        setLoading(false);
      });
  }, [fundCode]);

  if (loading) {
    return (
      <Card style={{ textAlign: 'center', padding: 60 }}>
        <Spin size="large" />
        <div style={{ marginTop: 16, color: '#666' }}>
          <RobotOutlined style={{ marginRight: 8 }} />
          AI 正在分析基金数据，请稍候...
        </div>
      </Card>
    );
  }

  if (error || !diagnosis) {
    return (
      <Alert
        message="AI 诊断暂时不可用"
        description={error || '无法获取诊断报告'}
        type="warning"
        showIcon
      />
    );
  }

  // 获取推荐标签颜色和文字
  const getRecommendationConfig = (rec: string) => {
    switch (rec) {
      case 'bullish':
        return { color: 'success', text: '看涨', icon: <CheckCircleOutlined /> };
      case 'bearish':
        return { color: 'error', text: '看跌', icon: <WarningOutlined /> };
      default:
        return { color: 'default', text: '中性', icon: <InfoCircleOutlined /> };
    }
  };

  const recConfig = getRecommendationConfig(diagnosis.recommendation);

  // 获取建议颜色
  const getSuggestionColor = (suggestion: string) => {
    switch (suggestion) {
      case '增持': return 'green';
      case '减持': return 'red';
      case '观望': return 'orange';
      default: return 'blue';
    }
  };

  // 渲染星级
  const renderStars = (level: number) => {
    return (
      <Space>
        {[1, 2, 3, 4, 5].map((i) => (
          i <= level ?
            <StarFilled key={i} style={{ color: '#faad14' }} /> :
            <StarOutlined key={i} style={{ color: '#d9d9d9' }} />
        ))}
      </Space>
    );
  };

  return (
    <div className="ai-diagnosis-tab">
      {/* 综合评分卡片 */}
      <Card style={{ marginBottom: 16, background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }}>
        <Row align="middle" gutter={24}>
          <Col xs={24} sm={8} style={{ textAlign: 'center' }}>
            <div style={{ color: '#fff' }}>
              <div style={{ fontSize: 14, opacity: 0.9, marginBottom: 8 }}>综合评分</div>
              <div style={{ fontSize: 56, fontWeight: 700, lineHeight: 1 }}>
                {diagnosis.overallScore}
              </div>
              <div style={{ fontSize: 14, opacity: 0.9, marginTop: 8 }}>满分 100</div>
            </div>
          </Col>
          <Col xs={24} sm={16}>
            <div style={{ color: '#fff' }}>
              <Space align="center" style={{ marginBottom: 12 }}>
                <Title level={4} style={{ color: '#fff', margin: 0 }}>{diagnosis.fundName}</Title>
                <Tag color={recConfig.color} icon={recConfig.icon} style={{ fontSize: 14, padding: '4px 12px' }}>
                  {recConfig.text}
                </Tag>
              </Space>
              <div style={{ marginBottom: 12 }}>
                <Text style={{ color: 'rgba(255,255,255,0.9)' }}>投资信心: </Text>
                {renderStars(diagnosis.confidenceLevel)}
              </div>
              <Paragraph style={{ color: 'rgba(255,255,255,0.95)', fontSize: 14, margin: 0 }}>
                {diagnosis.summary}
              </Paragraph>
              <div style={{ marginTop: 8, fontSize: 12, opacity: 0.7 }}>
                诊断时间: {diagnosis.diagnosisTime}
              </div>
            </div>
          </Col>
        </Row>
      </Card>

      {/* 多维度评分 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} md={8}>
          <Card title="估值合理性" size="small">
            <Progress percent={diagnosis.dimensionScores.valuation} status="active" />
            <div style={{ marginTop: 8, color: '#666' }}>
              <Badge status={diagnosis.dimensionScores.valuation >= 70 ? 'success' : diagnosis.dimensionScores.valuation >= 50 ? 'warning' : 'error'} />
              {diagnosis.valuation.status}
            </div>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8}>
          <Card title="业绩表现" size="small">
            <Progress percent={diagnosis.dimensionScores.performance} status="active" strokeColor="#52c41a" />
            <div style={{ marginTop: 8, color: '#666' }}>
              <Badge status={diagnosis.dimensionScores.performance >= 70 ? 'success' : diagnosis.dimensionScores.performance >= 50 ? 'warning' : 'error'} />
              {diagnosis.performance.shortTerm}
            </div>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8}>
          <Card title="风险控制" size="small">
            <Progress percent={diagnosis.dimensionScores.risk} status="active" strokeColor="#faad14" />
            <div style={{ marginTop: 8, color: '#666' }}>
              风险等级: {diagnosis.risk.riskLevel}/5
            </div>
          </Card>
        </Col>
      </Row>

      {/* 详细分析 */}
      <Row gutter={16}>
        {/* 估值分析 */}
        <Col xs={24} lg={12} style={{ marginBottom: 16 }}>
          <Card title="📊 估值分析" size="small">
            <Space direction="vertical" style={{ width: '100%' }}>
              <Row>
                <Col span={12}>
                  <Statistic title="估值状态" value={diagnosis.valuation.status} valueStyle={{ fontSize: 16 }} />
                </Col>
                <Col span={12}>
                  <Statistic title="PE历史分位" value={`${diagnosis.valuation.pePercentile}%`} valueStyle={{ fontSize: 16 }} />
                </Col>
              </Row>
              <Paragraph type="secondary" style={{ marginTop: 8 }}>
                {diagnosis.valuation.description}
              </Paragraph>
            </Space>
          </Card>
        </Col>

        {/* 业绩分析 */}
        <Col xs={24} lg={12} style={{ marginBottom: 16 }}>
          <Card title="📈 业绩分析" size="small">
            <Space direction="vertical" style={{ width: '100%' }}>
              <Row>
                <Col span={8}>
                  <Statistic title="短期" value={diagnosis.performance.shortTerm} valueStyle={{ fontSize: 14 }} />
                </Col>
                <Col span={8}>
                  <Statistic title="中期" value={diagnosis.performance.midTerm} valueStyle={{ fontSize: 14 }} />
                </Col>
                <Col span={8}>
                  <Statistic title="长期" value={diagnosis.performance.longTerm} valueStyle={{ fontSize: 14 }} />
                </Col>
              </Row>
              <div style={{ marginTop: 8 }}>
                <Tag color={diagnosis.performance.vsBenchmark.includes('跑赢') ? 'green' : 'orange'}>
                  {diagnosis.performance.vsBenchmark}
                </Tag>
              </div>
              <Paragraph type="secondary">
                {diagnosis.performance.description}
              </Paragraph>
            </Space>
          </Card>
        </Col>

        {/* 风险分析 */}
        <Col xs={24} lg={12} style={{ marginBottom: 16 }}>
          <Card title="⚠️ 风险分析" size="small">
            <Space direction="vertical" style={{ width: '100%' }}>
              <Row>
                <Col span={8}>
                  <Statistic title="风险等级" value={`${diagnosis.risk.riskLevel}/5`} valueStyle={{ fontSize: 14 }} />
                </Col>
                <Col span={8}>
                  <Statistic title="波动率" value={diagnosis.risk.volatility} valueStyle={{ fontSize: 14 }} />
                </Col>
                <Col span={8}>
                  <Statistic title="最大回撤" value={diagnosis.risk.maxDrawdown} valueStyle={{ fontSize: 14 }} />
                </Col>
              </Row>
              <Paragraph type="secondary">
                {diagnosis.risk.description}
              </Paragraph>
            </Space>
          </Card>
        </Col>

        {/* 持仓建议 */}
        <Col xs={24} lg={12} style={{ marginBottom: 16 }}>
          <Card title="💡 持仓建议" size="small">
            <Space direction="vertical" style={{ width: '100%' }}>
              <div>
                <Tag color={getSuggestionColor(diagnosis.positionAdvice.suggestion)} style={{ fontSize: 16, padding: '4px 12px' }}>
                  {diagnosis.positionAdvice.suggestion}
                </Tag>
                <span style={{ marginLeft: 12, color: '#666' }}>
                  建议仓位: {diagnosis.positionAdvice.suggestedRatio}%
                </span>
              </div>
              <Paragraph type="secondary">
                {diagnosis.positionAdvice.reason}
              </Paragraph>
            </Space>
          </Card>
        </Col>
      </Row>

      {/* 风险提示 */}
      {diagnosis.riskWarnings && diagnosis.riskWarnings.length > 0 && (
        <Card title="⚠️ 风险提示" size="small" style={{ marginBottom: 16 }}>
          <List
            size="small"
            dataSource={diagnosis.riskWarnings}
            renderItem={(item) => (
              <List.Item>
                <Text type="warning"><WarningOutlined style={{ marginRight: 8 }} />{item}</Text>
              </List.Item>
            )}
          />
        </Card>
      )}

      {/* 适合人群 */}
      <Row gutter={16}>
        <Col xs={24} sm={12}>
          <Card title="✅ 适合人群" size="small">
            <List
              size="small"
              dataSource={diagnosis.suitableFor}
              renderItem={(item) => (
                <List.Item>
                  <Text type="success"><CheckCircleOutlined style={{ marginRight: 8 }} />{item}</Text>
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12}>
          <Card title="❌ 不适合人群" size="small">
            <List
              size="small"
              dataSource={diagnosis.notSuitableFor}
              renderItem={(item) => (
                <List.Item>
                  <Text type="secondary">{item}</Text>
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>

      <Divider />

      <div style={{ textAlign: 'center', color: '#999', fontSize: 12 }}>
        <InfoCircleOutlined style={{ marginRight: 4 }} />
        AI 诊断报告仅供参考，不构成投资建议。投资有风险，入市需谨慎。
      </div>
    </div>
  );
};

export default AiDiagnosisTab;
