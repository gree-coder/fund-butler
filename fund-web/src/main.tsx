import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import './index.css';
import './App.css';
import { registerFundTheme } from './utils/chartTheme';
import App from './App.tsx';

registerFundTheme();

// 创建 QueryClient 实例
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30 * 1000, // 默认30秒内数据视为新鲜
      retry: 2,
      refetchOnWindowFocus: false, // 窗口聚焦时不自动刷新
    },
  },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
);
