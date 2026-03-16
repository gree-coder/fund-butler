import client from './client';

export interface WatchlistItem {
  id: number;
  fundCode: string;
  fundName: string;
  groupName: string;
  latestNav: number;
  estimateReturn: number;
  performance: { month1: number; month3: number; year1: number };
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
};
