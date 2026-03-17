import { create } from 'zustand';
import type { FundSearchItem } from '../api/fund';

interface SearchStore {
  keyword: string;
  results: FundSearchItem[];
  setSearch: (keyword: string, results: FundSearchItem[]) => void;
}

export const useSearchStore = create<SearchStore>((set) => ({
  keyword: '',
  results: [],
  setSearch: (keyword, results) => set({ keyword, results }),
}));
