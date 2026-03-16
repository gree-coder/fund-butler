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

const theme = {
  token: {
    colorPrimary: '#1677FF',
    borderRadius: 8,
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
          </Route>
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  );
};

export default App;
