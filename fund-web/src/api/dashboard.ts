import client from './client';

export interface IndustryItem {
  industry: string;
  ratio: number;
  marketValue: number;
}

export interface DashboardData {
  totalAsset: number;
  totalProfit: number;
  totalProfitRate: number;
  todayProfit: number;
  /** 今日收益是否为预估值 */
  todayProfitIsEstimate: boolean;
  /** 今日预估总收益金额 */
  todayEstimateProfit: number;
  /** 今日预估涨幅 */
  todayEstimateReturn: number;
  positions: PositionItem[];
  /** 聚合行业分布 */
  industryDistribution: IndustryItem[];
}

export interface PositionItem {
  id: number;
  fundCode: string;
  fundName: string;
  fundType: string;
  shares: number;
  costAmount: number;
  latestNav: number;
  estimateNav: number;
  estimateReturn: number;
  /** 今日预估收益金额 */
  estimateProfit: number;
  actualNav?: number;
  actualReturn?: number;
  actualReturnDelayed?: boolean;
  actualNavDate?: string;
  marketValue: number;
  profit: number;
  profitRate: number;
  accountId: number;
  accountName: string;
  /** 行业分布 */
  industryDist?: { industry: string; ratio: number }[];
}

export interface ProfitTrend {
  dates: string[];
  profits: number[];
}

export interface DrawdownData {
  maxDrawdown: number;
  maxDrawdownAmount: number;
  startDate: string | null;
  endDate: string | null;
  duration: number;
  drawdownCurve: number[];
}

export interface PerformanceMetrics {
  totalReturn: number;
  annualizedReturn: number;
  sharpeRatio: number;
  volatility: number;
  profitDays: number;
  lossDays: number;
  winRate: number;
}

export interface ProfitAnalysisData {
  dates: string[];
  dailyProfits: number[];
  cumulativeProfits: number[];
  cumulativeReturns: number[];
  marketValues: number[];
  drawdown: DrawdownData;
  metrics: PerformanceMetrics;
}

// 风险预警相关接口
export interface RiskItem {
  type: string;
  level: 'critical' | 'high' | 'medium' | 'low';
  title: string;
  description: string;
  relatedItems: string[];
  currentValue: string;
  threshold: string;
  suggestion: string;
}

// 市场概览相关接口
export interface IndexData {
  code: string;
  name: string;
  currentPoint: number;
  changePoint: number;
  changePercent: number;
  volume: number;
  turnover: number;
  trend: 'up' | 'down' | 'flat';
}

export interface SectorData {
  name: string;
  changePercent: number;
  leadingStock: string;
  trend: 'up' | 'down';
}

export interface PortfolioImpact {
  overallImpact: 'positive' | 'neutral' | 'negative';
  description: string;
  relatedFundCount: number;
  suggestion: string;
}

export interface MarketOverviewData {
  updateTime: string;
  marketSentiment: 'bullish' | 'neutral' | 'bearish';
  sentimentDescription: string;
  indices: IndexData[];
  leadingSectors: SectorData[];
  decliningSectors: SectorData[];
  portfolioImpact: PortfolioImpact;
}

// 调仓时机提醒相关接口
export interface TimingAlert {
  type: 'buy' | 'sell' | 'hold' | 'watch';
  priority: 'high' | 'medium' | 'low';
  fundCode: string;
  fundName: string;
  triggerCondition: string;
  valuationStatus: 'undervalued' | 'fair' | 'overvalued';
  suggestedAction: string;
  positionAdjustment: string;
  reason: string;
  expectedOutcome: string;
}

export interface FundRebalanceAdvice {
  fundCode: string;
  fundName: string;
  currentRatio: number;
  suggestedRatio: number;
  adjustmentDirection: 'increase' | 'decrease' | 'maintain';
  adjustmentRange: string;
  valuationPercentile: number;
  recentPerformance: string;
  reason: string;
}

export interface MarketOpportunity {
  type: string;
  description: string;
  suggestedAction: string;
  urgency: 'immediate' | 'short-term' | 'long-term';
}

export interface RebalanceTimingData {
  analysisTime: string;
  marketSentiment: 'bullish' | 'neutral' | 'bearish';
  summary: string;
  alerts: TimingAlert[];
  fundAdvices: FundRebalanceAdvice[];
  opportunities: MarketOpportunity[];
  riskReminders: string[];
}

export interface HealthMetrics {
  totalPositions: number;
  industryDiversification: number;
  concentrationScore: number;
  riskBalanceScore: number;
  valuationHealthScore: number;
  overallHealth: number;
}

export interface OptimizationSuggestion {
  type: string;
  priority: 'high' | 'medium' | 'low';
  title: string;
  content: string;
  expectedBenefit: string;
}

export interface RiskWarningData {
  warningTime: string;
  overallRiskLevel: 'low' | 'medium' | 'high';
  riskScore: number;
  summary: string;
  risks: RiskItem[];
  healthMetrics: HealthMetrics;
  suggestions: OptimizationSuggestion[];
}

export const dashboardApi = {
  getData: (): Promise<DashboardData> =>
    client.get('/dashboard'),

  getProfitTrend: (days: number): Promise<ProfitTrend> =>
    client.get('/dashboard/profit-trend', { params: { days } }),

  getProfitAnalysis: (days: number): Promise<ProfitAnalysisData> =>
    client.get('/dashboard/profit-analysis', { params: { days } }),

  // 风险预警接口
  getRiskWarning: (): Promise<RiskWarningData> =>
    client.get('/risk/warning'),

  // 调仓时机提醒接口
  getRebalanceTiming: (): Promise<RebalanceTimingData> =>
    client.get('/rebalance/timing'),

  // 市场概览接口
  getMarketOverview: (): Promise<MarketOverviewData> =>
    client.get('/market/overview'),
};
