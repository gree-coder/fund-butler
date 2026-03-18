import client from './client';

export interface WatchlistItem {
  id: number;
  fundCode: string;
  fundName: string;
  groupName: string;
  latestNav: number;
  estimateReturn: number;
  actualNav?: number;
  actualReturn?: number;
  performance: { month1: number; month3: number; year1: number };
  smartEstimateReturn?: number | null;
  smartEstimateNav?: number | null;
  smartStrategyType?: string | null;
  smartDescription?: string | null;
  smartScenario?: string | null;
  smartWeights?: Record<string, number> | null;
  smartAccuracyEnhanced?: boolean;
}

export interface WatchlistData {
  list: WatchlistItem[];
  groups: string[];
}

export const watchlistApi = {
  list: (group?: string): Promise<WatchlistData> =>
    client.get('/watchlist', { params: group ? { group } : {} }),

  add: (data: { fundCode: string; groupName?: string }): Promise<void> =>
    client.post('/watchlist', data),

  remove: (id: number): Promise<void> =>
    client.delete(`/watchlist/${id}`),

  checkExists: (codes: string[]): Promise<string[]> =>
    client.get('/watchlist/check', { params: { codes: codes.join(',') } }),
};
