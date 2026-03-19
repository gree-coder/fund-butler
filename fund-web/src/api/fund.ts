import client from './client';

export interface FundSearchItem {
  code: string;
  name: string;
  type: string;
}

export interface FundDetail {
  code: string;
  name: string;
  type: string;
  company: string;
  manager: string;
  establishDate: string;
  scale: number;
  riskLevel: number;
  feeRate: { purchaseRate?: string; discountRate?: string; managementFee?: string; custodyFee?: string };
  topHoldings: { stockCode: string; stockName: string; ratio: number; changePercent?: number; currentPrice?: number; industry?: string }[];
  industryDist: { industry: string; ratio: number }[];
  sectorChanges: { sectorName: string; changePercent: number }[];
  latestNav: number;
  latestNavDate: string;
  estimateNav: number;
  estimateReturn: number;
  estimateSource: string;
  performance: {
    week1: number;
    month1: number;
    month3: number;
    month6: number;
    year1: number;
    year3: number;
    sinceEstablish: number;
  };
}

export interface NavHistory {
  dates: string[];
  navs: number[];
}

export interface EstimateItem {
  key: string;
  label: string;
  estimateNav: number;
  estimateReturn: number;
  available: boolean;
  description: string;
  strategyType?: string | null;
  scenario?: string | null;
  weights?: Record<string, number> | null;
  accuracyEnhanced?: boolean;
  delayed?: boolean;
}

export interface EstimateSourceData {
  sources: EstimateItem[];
}

export interface RefreshResult {
  detail: FundDetail;
  estimates: EstimateSourceData;
}

export const fundApi = {
  search: (keyword: string): Promise<{ list: FundSearchItem[] }> =>
    client.get('/fund/search', { params: { keyword } }),

  getDetail: (code: string): Promise<FundDetail> =>
    client.get(`/fund/${code}`),

  getNavHistory: (code: string, period: string): Promise<NavHistory> =>
    client.get(`/fund/${code}/nav-history`, { params: { period } }),

  getEstimates: (code: string): Promise<EstimateSourceData> =>
    client.get(`/fund/${code}/estimates`),

  refreshData: (code: string): Promise<RefreshResult> =>
    client.post(`/fund/${code}/refresh`),
};
