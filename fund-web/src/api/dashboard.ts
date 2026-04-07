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

export const dashboardApi = {
  getData: (): Promise<DashboardData> =>
    client.get('/dashboard'),

  getProfitTrend: (days: number): Promise<ProfitTrend> =>
    client.get('/dashboard/profit-trend', { params: { days } }),
};
