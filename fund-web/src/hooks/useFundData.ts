import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { dashboardApi } from '../api/dashboard';
import { fundApi } from '../api/fund';
import { positionApi } from '../api/position';
import { watchlistApi } from '../api/watchlist';

// Query keys
export const queryKeys = {
  dashboard: ['dashboard'] as const,
  fundDetail: (code: string) => ['fund', code] as const,
  fundSearch: (keyword: string) => ['fundSearch', keyword] as const,
  positions: (accountId?: number) => ['positions', accountId] as const,
  watchlist: ['watchlist'] as const,
  profitTrend: (days: number) => ['profitTrend', days] as const,
};

// Dashboard 数据 Hook
export function useDashboardData() {
  return useQuery({
    queryKey: queryKeys.dashboard,
    queryFn: dashboardApi.getData,
    staleTime: 30 * 1000, // 30秒内数据视为新鲜
    refetchInterval: 60 * 1000, // 每分钟自动刷新
    retry: 2,
  });
}

// 收益趋势 Hook
export function useProfitTrend(days: number) {
  return useQuery({
    queryKey: queryKeys.profitTrend(days),
    queryFn: () => dashboardApi.getProfitTrend(days),
    staleTime: 5 * 60 * 1000, // 5分钟
    enabled: days > 0,
  });
}

// 基金详情 Hook
export function useFundDetail(fundCode: string) {
  return useQuery({
    queryKey: queryKeys.fundDetail(fundCode),
    queryFn: () => fundApi.getDetail(fundCode),
    staleTime: 60 * 1000, // 1分钟
    enabled: !!fundCode,
    retry: 2,
  });
}

// 基金搜索 Hook（带防抖）
export function useFundSearch(keyword: string) {
  return useQuery({
    queryKey: queryKeys.fundSearch(keyword),
    queryFn: () => fundApi.search(keyword),
    staleTime: 5 * 60 * 1000, // 5分钟
    enabled: keyword.length >= 2, // 至少2个字符才搜索
    retry: 1,
  });
}

// 持仓列表 Hook
export function usePositions(accountId?: number) {
  return useQuery({
    queryKey: queryKeys.positions(accountId),
    queryFn: () => positionApi.list(accountId),
    staleTime: 30 * 1000, // 30秒
    refetchInterval: 60 * 1000, // 每分钟自动刷新
    retry: 2,
  });
}

// 自选基金 Hook
export function useWatchlist(group?: string) {
  return useQuery({
    queryKey: [...queryKeys.watchlist, group],
    queryFn: () => watchlistApi.list(group),
    staleTime: 30 * 1000,
    refetchInterval: 60 * 1000,
    retry: 2,
  });
}

// 添加自选 Mutation
export function useAddWatchlist() {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: watchlistApi.add,
    onSuccess: () => {
      // 成功后刷新自选列表
      queryClient.invalidateQueries({ queryKey: queryKeys.watchlist });
    },
  });
}

// 删除自选 Mutation
export function useRemoveWatchlist() {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: watchlistApi.remove,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.watchlist });
    },
  });
}

// 添加持仓 Mutation
export function useAddPosition() {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: positionApi.add,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.positions() });
      queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
    },
  });
}

// 手动刷新所有基金数据
export function useRefreshFundData() {
  const queryClient = useQueryClient();
  
  return () => {
    queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    queryClient.invalidateQueries({ queryKey: ['positions'] });
    queryClient.invalidateQueries({ queryKey: ['watchlist'] });
    queryClient.invalidateQueries({ queryKey: ['fund'] });
  };
}
