import React, { useRef, useEffect } from 'react';
import { Layout, Menu } from 'antd';
import {
  DashboardOutlined,
  SearchOutlined,
  FundOutlined,
  StarOutlined,
  LineChartOutlined,
} from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import SearchBar from './SearchBar';

const { Header, Content, Sider } = Layout;

const menuItems = [
  { key: '/', icon: <DashboardOutlined />, label: '首页' },
  { key: '/search', icon: <SearchOutlined />, label: '基金查询' },
  { key: '/portfolio', icon: <FundOutlined />, label: '我的持仓' },
  { key: '/watchlist', icon: <StarOutlined />, label: '自选基金' },
  { key: '/analysis', icon: <LineChartOutlined />, label: '收益分析' },
];

const AppLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const lastSearchPath = useRef<string>('/search');

  useEffect(() => {
    const path = location.pathname;
    if (path.startsWith('/search') || path.startsWith('/fund/')) {
      lastSearchPath.current = path + location.search;
    }
  }, [location.pathname, location.search]);

  const handleMenuClick = ({ key }: { key: string }) => {
    if (key === '/search') {
      navigate(lastSearchPath.current);
    } else {
      navigate(key);
    }
  };

  const selectedKey = location.pathname.startsWith('/fund/')
    ? '/search'
    : menuItems.find((item) =>
      item.key === '/'
        ? location.pathname === '/'
        : location.pathname.startsWith(item.key),
    )?.key || '/';

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        className="fund-header"
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          background: '#fff',
          padding: '0 24px',
          height: 64,
          lineHeight: '64px',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            fontSize: 18,
            fontWeight: 700,
            color: '#1677FF',
            cursor: 'pointer',
            letterSpacing: 1,
          }}
          onClick={() => navigate('/')}
        >
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 32,
            height: 32,
            borderRadius: 8,
            background: 'linear-gradient(135deg, #1677FF, #4096ff)',
            color: '#fff',
            fontSize: 16,
          }}>
            <FundOutlined />
          </span>
          基金管家
        </div>
        <SearchBar />
      </Header>
      <Layout>
        <Sider
          width={200}
          className="fund-sider"
          style={{ background: '#fff' }}
        >
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
            items={menuItems}
            style={{ height: '100%', borderRight: 0, paddingTop: 8 }}
            onClick={handleMenuClick}
          />
        </Sider>
        <Content
          style={{
            padding: 24,
            background: '#F5F7FA',
            minHeight: 'calc(100vh - 64px)',
          }}
        >
          <div className="fund-content">
            <Outlet />
            <div className="fund-footer">
              数据仅供参考，不构成投资建议
            </div>
          </div>
        </Content>
      </Layout>
    </Layout>
  );
};

export default AppLayout;
