import client from './client';

export interface DashboardData {
  totalAsset: number;
  totalProfit: number;
  totalProfitRate: number;
  todayProfit: number;
  positions: PositionItem[];
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
  actualNav?: number;
  actualReturn?: number;
  actualReturnDelayed?: boolean;
  marketValue: number;
  profit: number;
  profitRate: number;
  accountId: number;
  accountName: string;
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
