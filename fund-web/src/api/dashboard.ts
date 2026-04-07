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

export const dashboardApi = {
  getData: (): Promise<DashboardData> =>
    client.get('/dashboard'),

  getProfitTrend: (days: number): Promise<ProfitTrend> =>
    client.get('/dashboard/profit-trend', { params: { days } }),

  getProfitAnalysis: (days: number): Promise<ProfitAnalysisData> =>
    client.get('/dashboard/profit-analysis', { params: { days } }),
};
