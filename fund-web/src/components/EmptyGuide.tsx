import React from 'react';
import { Button, Empty } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';

const EmptyGuide: React.FC = () => {
  const navigate = useNavigate();

  return (
    <Empty
      image={Empty.PRESENTED_IMAGE_SIMPLE}
      description="还没有持仓基金"
      style={{ padding: '60px 0' }}
    >
      <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => navigate('/portfolio/add')}
        >
          添加持仓
        </Button>
        <Button
          icon={<SearchOutlined />}
          onClick={() => navigate('/search')}
        >
          搜索基金
        </Button>
      </div>
    </Empty>
  );
};

export default EmptyGuide;
