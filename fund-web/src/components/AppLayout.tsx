import React from 'react';
import { Layout, Menu } from 'antd';
import {
  DashboardOutlined,
  SearchOutlined,
  FundOutlined,
  StarOutlined,
} from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import SearchBar from './SearchBar';

const { Header, Content, Sider } = Layout;

const menuItems = [
  { key: '/', icon: <DashboardOutlined />, label: '首页' },
  { key: '/search', icon: <SearchOutlined />, label: '基金查询' },
  { key: '/portfolio', icon: <FundOutlined />, label: '我的持仓' },
  { key: '/watchlist', icon: <StarOutlined />, label: '自选基金' },
];

const AppLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();

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
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          background: '#fff',
          borderBottom: '1px solid #f0f0f0',
          padding: '0 24px',
        }}
      >
        <div
          style={{
            fontSize: 18,
            fontWeight: 700,
            color: '#1677FF',
            cursor: 'pointer',
          }}
          onClick={() => navigate('/')}
        >
          基金管家
        </div>
        <SearchBar />
      </Header>
      <Layout>
        <Sider
          width={200}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0' }}
        >
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
            items={menuItems}
            style={{ height: '100%', borderRight: 0 }}
            onClick={({ key }) => navigate(key)}
          />
        </Sider>
        <Content
          style={{
            padding: 24,
            background: '#F5F7FA',
            minHeight: 'calc(100vh - 64px)',
          }}
        >
          <Outlet />
          <div
            style={{
              textAlign: 'center',
              color: '#999',
              fontSize: 12,
              marginTop: 40,
              paddingBottom: 20,
            }}
          >
            数据仅供参考，不构成投资建议
          </div>
        </Content>
      </Layout>
    </Layout>
  );
};

export default AppLayout;
