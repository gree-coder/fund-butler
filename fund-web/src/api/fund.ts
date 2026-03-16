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
  feeRate: { buy: number; sell: number; manage: number; custody: number };
  topHoldings: { stockCode: string; stockName: string; ratio: number }[];
  industryDist: { industry: string; ratio: number }[];
  latestNav: number;
  latestNavDate: string;
  estimateNav: number;
  estimateReturn: number;
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

export const fundApi = {
  search: (keyword: string): Promise<{ list: FundSearchItem[] }> =>
    client.get('/fund/search', { params: { keyword } }),

  getDetail: (code: string): Promise<FundDetail> =>
    client.get(`/fund/${code}`),

  getNavHistory: (code: string, period: string): Promise<NavHistory> =>
    client.get(`/fund/${code}/nav-history`, { params: { period } }),
};
