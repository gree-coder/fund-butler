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
  holdingsDate?: string;
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
  delayedDate?: string;
}

export interface EstimateSourceData {
  sources: EstimateItem[];
}

export interface RefreshResult {
  detail: FundDetail;
  estimates: EstimateSourceData;
}

// 基金诊断报告接口
export interface FundDiagnosis {
  fundCode: string;
  fundName: string;
  diagnosisTime: string;
  overallScore: number;
  recommendation: 'bullish' | 'neutral' | 'bearish';
  confidenceLevel: number;
  summary: string;
  dimensionScores: {
    valuation: number;
    performance: number;
    risk: number;
    stability: number;
    cost: number;
  };
  valuation: {
    status: string;
    pePercentile: number;
    pbPercentile: number;
    description: string;
  };
  performance: {
    shortTerm: string;
    midTerm: string;
    longTerm: string;
    vsBenchmark: string;
    description: string;
  };
  risk: {
    riskLevel: number;
    volatility: string;
    maxDrawdown: string;
    description: string;
  };
  positionAdvice: {
    suggestion: string;
    reason: string;
    suggestedRatio: number;
  };
  riskWarnings: string[];
  suitableFor: string[];
  notSuitableFor: string[];
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

  // 诊断接口
  getDiagnosis: (code: string): Promise<FundDiagnosis> =>
    client.get(`/report/fund/${code}/diagnosis`),
};
