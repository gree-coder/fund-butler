import React from 'react';
import { Button, Empty } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';

const EmptyGuide: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div className="fund-empty-guide">
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description={<span style={{ fontSize: 15, color: '#8C8C8C' }}>还没有持仓基金</span>}
      />
      <div className="fund-empty-actions">
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
    </div>
  );
};

export default EmptyGuide;
