import client from './client';

export interface EstimateAnalysisData {
  fundCode: string;
  fundName: string;
  currentEstimates: CurrentEstimate;
  accuracyStats: AccuracyStats;
  compensationLogs: CompensationLog[];
}

export interface CurrentEstimate {
  actualNav?: number;
  actualReturn?: number;
  actualNavDate?: string;
  actualReturnDelayed?: boolean;
  sources: SourceEstimate[];
  smartEstimate: SmartEstimate;
  snapshotTime?: string;
}

export interface SourceEstimate {
  key: string;
  label: string;
  estimateNav?: number;
  estimateReturn?: number;
  available: boolean;
  weight?: number;
  confidence?: number;
  description?: string;
}

export interface SmartEstimate {
  nav?: number;
  returnRate?: number;
  strategy?: string;
  scenario?: string;
  accuracyEnhanced: boolean;
  weights?: Record<string, number>;
  baseWeights?: Record<string, number>;
  description?: string;
}

export interface AccuracyStats {
  period: string;
  sources: SourceAccuracy[];
}

export interface SourceAccuracy {
  key: string;
  label: string;
  mae: number;
  predictionCount: number;
  hitRate: number;
  trend: 'improving' | 'stable' | 'declining';
  rating: number;
}

export interface CompensationLog {
  date: string;
  beforeNav?: number;
  beforeReturn?: number;
  afterNav?: number;
  afterReturn?: number;
  source: string;
  type: 'PREDICT' | 'ACTUAL';
  compensatedAt?: string;
  reason: string;
}

export const estimateAnalysisApi = {
  getAnalysis: (fundCode: string): Promise<EstimateAnalysisData> =>
    client.get(`/fund/${fundCode}/estimate-analysis`),
};
