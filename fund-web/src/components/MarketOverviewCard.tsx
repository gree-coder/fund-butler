import React, { useEffect, useState } from 'react';
import { Card, Row, Col, Tag, Space, Typography } from 'antd';
import { 
  RiseOutlined, 
  FallOutlined, 
  MinusOutlined,
  StockOutlined,
  FireOutlined,
  ThunderboltOutlined
} from '@ant-design/icons';
import { dashboardApi, type MarketOverviewData, type IndexData, type SectorData } from '../api/dashboard';

const { Text } = Typography;

const MarketOverviewCard: React.FC = () => {
  const [overview, setOverview] = useState<MarketOverviewData | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    dashboardApi.getMarketOverview()
      .then((data) => {
        setOverview(data);
      })
      .catch(() => {
        // 静默失败
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  if (loading || !overview) {
    return null;
  }

  // 获取市场情绪配置
  const getSentimentConfig = (sentiment: string) => {
    switch (sentiment) {
      case 'bullish':
        return { 
          color: '#cf1322', 
          bgColor: '#fff1f0',
          icon: <RiseOutlined />, 
          text: '积极'
        };
      case 'bearish':
        return { 
          color: '#389e0d', 
          bgColor: '#f6ffed',
          icon: <FallOutlined />, 
          text: '谨慎'
        };
      default:
        return { 
          color: '#666', 
          bgColor: '#f5f5f5',
          icon: <MinusOutlined />, 
          text: '中性'
        };
    }
  };



  // 获取涨跌幅颜色
  const getChangeColor = (value: number) => {
    if (value > 0) return '#cf1322';
    if (value < 0) return '#389e0d';
    return '#666';
  };

  // 获取持仓影响配置
  const getImpactConfig = (impact: string) => {
    switch (impact) {
      case 'positive':
        return { color: 'success', text: '正面影响', icon: <RiseOutlined /> };
      case 'negative':
        return { color: 'error', text: '负面影响', icon: <FallOutlined /> };
      default:
        return { color: 'default', text: '影响中性', icon: <MinusOutlined /> };
    }
  };

  const sentimentConfig = getSentimentConfig(overview.marketSentiment);
  const impactConfig = overview.portfolioImpact ? 
    getImpactConfig(overview.portfolioImpact.overallImpact) : null;

  return (
    <Card
      className="market-overview-card"
      size="small"
      style={{
        borderRadius: 8,
        background: sentimentConfig.bgColor,
        height: '100%'
      }}
      title={
        <Space align="center">
          <StockOutlined style={{ fontSize: 16, color: sentimentConfig.color }} />
          <span style={{ fontSize: 14, fontWeight: 600 }}>市场概览</span>
          <Tag color={sentimentConfig.color} icon={sentimentConfig.icon} style={{ fontSize: 11 }}>
            {sentimentConfig.text}
          </Tag>
        </Space>
      }
      extra={
        <Text type="secondary" style={{ fontSize: 11 }}>
          {overview.updateTime?.slice(11, 16)}
        </Text>
      }
    >
      {/* 大盘指数 - 紧凑布局 */}
      {overview.indices && overview.indices.length > 0 && (
        <div style={{ marginBottom: 12 }}>
          <Row gutter={[8, 8]}>
            {overview.indices.slice(0, 4).map((index: IndexData) => (
              <Col span={12} key={index.code}>
                <div 
                  style={{ 
                    textAlign: 'center',
                    padding: '6px 4px',
                    borderRadius: 6,
                    background: getChangeColor(index.changePercent) + '10',
                    border: `1px solid ${getChangeColor(index.changePercent)}30`
                  }}
                >
                  <div style={{ fontSize: 11, color: '#666', marginBottom: 2 }}>
                    {index.name}
                  </div>
                  <div style={{ 
                    fontSize: 14, 
                    fontWeight: 600, 
                    color: getChangeColor(index.changePercent),
                    fontVariantNumeric: 'tabular-nums'
                  }}>
                    {index.changePercent > 0 ? '+' : ''}{index.changePercent.toFixed(2)}%
                  </div>
                </div>
              </Col>
            ))}
          </Row>
        </div>
      )}

      {/* 板块数据 - 紧凑列表 */}
      <Row gutter={[8, 0]}>
        {/* 领涨板块 */}
        {overview.leadingSectors && overview.leadingSectors.length > 0 && (
          <Col span={12}>
            <div style={{ marginBottom: 4 }}>
              <Space size={4}>
                <FireOutlined style={{ color: '#cf1322', fontSize: 12 }} />
                <Text strong style={{ fontSize: 12 }}>领涨</Text>
              </Space>
            </div>
            <div>
              {overview.leadingSectors.slice(0, 3).map((sector: SectorData, idx: number) => (
                <div 
                  key={idx}
                  style={{ 
                    display: 'flex', 
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    padding: '3px 0',
                    borderBottom: idx < 2 ? '1px solid #f0f0f0' : 'none',
                    fontSize: 11
                  }}
                >
                  <Text style={{ fontSize: 11 }} ellipsis>{sector.name}</Text>
                  <Text style={{ color: '#cf1322', fontSize: 11, fontWeight: 500 }}>
                    +{sector.changePercent.toFixed(2)}%
                  </Text>
                </div>
              ))}
            </div>
          </Col>
        )}

        {/* 领跌板块 */}
        {overview.decliningSectors && overview.decliningSectors.length > 0 && (
          <Col span={12}>
            <div style={{ marginBottom: 4 }}>
              <Space size={4}>
                <ThunderboltOutlined style={{ color: '#389e0d', fontSize: 12 }} />
                <Text strong style={{ fontSize: 12 }}>领跌</Text>
              </Space>
            </div>
            <div>
              {overview.decliningSectors.slice(0, 3).map((sector: SectorData, idx: number) => (
                <div 
                  key={idx}
                  style={{ 
                    display: 'flex', 
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    padding: '3px 0',
                    borderBottom: idx < 2 ? '1px solid #f0f0f0' : 'none',
                    fontSize: 11
                  }}
                >
                  <Text style={{ fontSize: 11 }} ellipsis>{sector.name}</Text>
                  <Text style={{ color: '#389e0d', fontSize: 11, fontWeight: 500 }}>
                    {sector.changePercent.toFixed(2)}%
                  </Text>
                </div>
              ))}
            </div>
          </Col>
        )}
      </Row>

      {/* 持仓影响 - 简化 */}
      {overview.portfolioImpact && (
        <div style={{ 
          marginTop: 8,
          padding: '6px 8px', 
          background: '#fff', 
          borderRadius: 6,
          border: '1px solid #f0f0f0'
        }}>
          <Space size={4}>
            {impactConfig?.icon}
            <Tag color={impactConfig?.color} style={{ fontSize: 10, padding: '0 4px' }}>
              {impactConfig?.text}
            </Tag>
            <Text style={{ fontSize: 11, color: '#666' }} ellipsis>
              {overview.portfolioImpact.suggestion}
            </Text>
          </Space>
        </div>
      )}
    </Card>
  );
};

export default MarketOverviewCard;
