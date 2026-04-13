import React, { useEffect, useState } from 'react';
import { Card, Tag, Typography, Space, Badge } from 'antd';
import { 
  RiseOutlined, 
  FallOutlined, 
  ClockCircleOutlined, 
  BulbOutlined,
  WarningOutlined,
  CheckCircleOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  MinusOutlined
} from '@ant-design/icons';
import { dashboardApi, type RebalanceTimingData, type TimingAlert, type FundRebalanceAdvice } from '../api/dashboard';

const { Text } = Typography;

const RebalanceTimingCard: React.FC = () => {
  const [timing, setTiming] = useState<RebalanceTimingData | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    dashboardApi.getRebalanceTiming()
      .then((data) => {
        setTiming(data);
      })
      .catch(() => {
        // 静默失败
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  if (loading || !timing) {
    return null;
  }

  // 获取市场情绪配置
  const getSentimentConfig = (sentiment: string) => {
    switch (sentiment) {
      case 'bullish':
        return { 
          color: 'red', 
          icon: <RiseOutlined />, 
          text: '积极',
          bgColor: '#fff1f0'
        };
      case 'bearish':
        return { 
          color: 'green', 
          icon: <FallOutlined />, 
          text: '谨慎',
          bgColor: '#f6ffed'
        };
      default:
        return { 
          color: 'default', 
          icon: <MinusOutlined />, 
          text: '中性',
          bgColor: '#f5f5f5'
        };
    }
  };

  // 获取提醒类型配置
  const getAlertTypeConfig = (type: string) => {
    switch (type) {
      case 'buy':
        return { color: 'green', icon: <ArrowUpOutlined />, text: '买入机会' };
      case 'sell':
        return { color: 'red', icon: <ArrowDownOutlined />, text: '止盈提醒' };
      case 'hold':
        return { color: 'blue', icon: <CheckCircleOutlined />, text: '继续持有' };
      default:
        return { color: 'default', icon: <ClockCircleOutlined />, text: '观望' };
    }
  };

  // 获取调整方向标签
  const getAdjustmentTag = (direction: string) => {
    switch (direction) {
      case 'increase':
        return <Tag color="success">建议增持</Tag>;
      case 'decrease':
        return <Tag color="warning">建议减持</Tag>;
      default:
        return <Tag color="default">维持现状</Tag>;
    }
  };

  const sentimentConfig = getSentimentConfig(timing.marketSentiment);
  const hasAlerts = timing.alerts && timing.alerts.length > 0;
  const hasAdvices = timing.fundAdvices && timing.fundAdvices.length > 0;

  return (
    <Card
      className="rebalance-timing-card"
      size="small"
      style={{
        borderRadius: 8,
        background: sentimentConfig.bgColor,
        height: '100%'
      }}
      title={
        <Space align="center">
          <ClockCircleOutlined style={{ fontSize: 16, color: sentimentConfig.color }} />
          <span style={{ fontSize: 14, fontWeight: 600 }}>调仓时机</span>
          <Tag color={sentimentConfig.color} icon={sentimentConfig.icon} style={{ fontSize: 11 }}>
            {sentimentConfig.text}
          </Tag>
        </Space>
      }
      extra={
        <Text type="secondary" style={{ fontSize: 11 }}>
          {timing.alerts?.length || 0}个提醒
        </Text>
      }
    >
      {/* 摘要 */}
      <div style={{ 
        padding: '8px', 
        background: '#fff', 
        borderRadius: 6, 
        marginBottom: 12,
        fontSize: 12,
        color: '#666'
      }}>
        {timing.summary}
      </div>

      {/* 可操作提醒 - 紧凑列表 */}
      {hasAlerts && (
        <div style={{ marginBottom: 12 }}>
          <Space size={4} style={{ marginBottom: 6 }}>
            <BulbOutlined style={{ color: '#faad14', fontSize: 12 }} />
            <Text strong style={{ fontSize: 12 }}>调仓提醒</Text>
          </Space>
          <div>
            {timing.alerts.slice(0, 3).map((alert: TimingAlert, idx: number) => {
              const alertConfig = getAlertTypeConfig(alert.type);
              return (
                <div 
                  key={idx}
                  style={{ 
                    padding: '6px 8px', 
                    background: '#fff', 
                    borderRadius: 4,
                    marginBottom: 4,
                    borderLeft: `2px solid ${alertConfig.color === 'green' ? '#52c41a' : alertConfig.color === 'red' ? '#ff4d4f' : '#1677ff'}`
                  }}
                >
                  <Space size={4}>
                    <Tag color={alertConfig.color} style={{ fontSize: 10, padding: '0 4px' }}>
                      {alertConfig.text}
                    </Tag>
                    <Text strong style={{ fontSize: 11 }} ellipsis>{alert.fundName}</Text>
                    {alert.priority === 'high' && <Badge status="error" />}
                  </Space>
                  <div style={{ fontSize: 10, color: '#666', marginTop: 2 }}>
                    {alert.suggestedAction}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* 持仓建议 - 简化 */}
      {hasAdvices && (
        <div style={{ marginBottom: 12 }}>
          <Space size={4} style={{ marginBottom: 6 }}>
            <RiseOutlined style={{ color: '#1677ff', fontSize: 12 }} />
            <Text strong style={{ fontSize: 12 }}>持仓建议</Text>
          </Space>
          <div>
            {/* 优先展示有调整建议的基金，最多3条 */}
            {timing.fundAdvices
              .filter((a: FundRebalanceAdvice) => a.adjustmentDirection !== 'maintain')
              .slice(0, 3)
              .map((advice: FundRebalanceAdvice, idx: number, arr: FundRebalanceAdvice[]) => (
                <div
                  key={idx}
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    padding: '4px 0',
                    borderBottom: idx < arr.length - 1 ? '1px solid #f0f0f0' : 'none'
                  }}
                >
                  <Text style={{ fontSize: 11 }} ellipsis>{advice.fundName}</Text>
                  <Space size={4}>
                    {getAdjustmentTag(advice.adjustmentDirection)}
                  </Space>
                </div>
              ))}
            {/* 如果没有调整建议，显示提示 */}
            {timing.fundAdvices.filter((a: FundRebalanceAdvice) => a.adjustmentDirection !== 'maintain').length === 0 && (
              <div style={{ padding: '8px 0', textAlign: 'center', color: '#999', fontSize: 11 }}>
                当前持仓结构合理，建议继续保持
              </div>
            )}
          </div>
        </div>
      )}

      {/* 风险提示 - 简化 */}
      {timing.riskReminders && timing.riskReminders.length > 0 && (
        <div style={{ 
          padding: '6px 8px', 
          background: '#fffbe6', 
          borderRadius: 6,
          border: '1px solid #ffe58f'
        }}>
          <Space size={4}>
            <WarningOutlined style={{ color: '#faad14', fontSize: 12 }} />
            <Text style={{ fontSize: 11, color: '#d48806' }}>
              {timing.riskReminders[0]}
            </Text>
          </Space>
        </div>
      )}
    </Card>
  );
};

export default RebalanceTimingCard;
