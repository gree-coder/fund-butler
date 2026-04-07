import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import AppLayout from './components/AppLayout';
import Dashboard from './pages/Dashboard';
import SearchResult from './pages/Fund/SearchResult';
import FundDetail from './pages/Fund/FundDetail';
import Portfolio from './pages/Portfolio';
import AddPosition from './pages/Portfolio/AddPosition';
import TransactionList from './pages/Portfolio/TransactionList';
import Watchlist from './pages/Watchlist';
import ProfitAnalysis from './pages/Analysis';

const theme = {
  token: {
    colorPrimary: '#1677FF',
    borderRadius: 8,
    colorBgContainer: '#FFFFFF',
    colorBgLayout: '#F5F7FA',
    fontSize: 14,
  },
  components: {
    Card: {
      paddingLG: 24,
      borderRadiusLG: 12,
    },
    Menu: {
      itemBorderRadius: 8,
      itemMarginInline: 8,
      itemSelectedBg: '#E6F4FF',
      itemSelectedColor: '#1677FF',
      itemHoverBg: '#FAFBFC',
    },
    Table: {
      headerBg: '#FAFBFC',
      borderColor: '#f0f0f0',
    },
    Tabs: {
      inkBarColor: '#1677FF',
    },
  },
};

const App: React.FC = () => {
  return (
    <ConfigProvider theme={theme} locale={zhCN}>
      <BrowserRouter>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/" element={<Dashboard />} />
            <Route path="/search" element={<SearchResult />} />
            <Route path="/fund/:code" element={<FundDetail />} />
            <Route path="/portfolio" element={<Portfolio />} />
            <Route path="/portfolio/add" element={<AddPosition />} />
            <Route path="/portfolio/records" element={<TransactionList />} />
            <Route path="/watchlist" element={<Watchlist />} />
            <Route path="/analysis" element={<ProfitAnalysis />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  );
};

export default App;
