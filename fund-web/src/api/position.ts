import client from './client';
import type { PositionItem } from './dashboard';

export interface AddPositionParams {
  fundCode: string;
  accountId: number;
  amount: number;
  shares: number;
  price: number;
  tradeDate: string;
}

export interface AddTransactionParams {
  type: 'BUY' | 'SELL' | 'DIVIDEND';
  amount: number;
  shares: number;
  price: number;
  fee?: number;
  tradeDate: string;
}

export interface TransactionItem {
  id: number;
  positionId: number;
  fundCode: string;
  type: string;
  amount: number;
  shares: number;
  price: number;
  fee: number;
  tradeDate: string;
}

export interface AccountItem {
  id: number;
  name: string;
  platform: string;
  icon: string;
}

export const positionApi = {
  list: (accountId?: number): Promise<PositionItem[]> =>
    client.get('/positions', { params: accountId ? { accountId } : {} }),

  add: (data: AddPositionParams): Promise<void> =>
    client.post('/positions', data),

  addTransaction: (id: number, data: AddTransactionParams): Promise<void> =>
    client.put(`/positions/${id}/transaction`, data),

  remove: (id: number): Promise<void> =>
    client.delete(`/positions/${id}`),

  getTransactions: (id: number): Promise<TransactionItem[]> =>
    client.get(`/positions/${id}/transactions`),
};

export const accountApi = {
  list: (): Promise<AccountItem[]> =>
    client.get('/accounts'),

  create: (data: { name: string; platform: string }): Promise<void> =>
    client.post('/accounts', data),

  remove: (id: number): Promise<void> =>
    client.delete(`/accounts/${id}`),
};
