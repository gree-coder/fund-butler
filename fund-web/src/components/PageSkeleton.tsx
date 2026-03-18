import React from 'react';
import { Skeleton, Row, Col } from 'antd';

interface PageSkeletonProps {
  type?: 'dashboard' | 'detail' | 'list' | 'form';
}

const PageSkeleton: React.FC<PageSkeletonProps> = ({ type = 'list' }) => {
  if (type === 'dashboard') {
    return (
      <div className="fund-fade-in">
        <Skeleton.Input active block style={{ height: 100, marginBottom: 16, borderRadius: 12 }} />
        <Row gutter={16}>
          <Col span={14}>
            <Skeleton active paragraph={{ rows: 6 }} />
          </Col>
          <Col span={10}>
            <Skeleton.Input active block style={{ height: 280, borderRadius: 12 }} />
          </Col>
        </Row>
      </div>
    );
  }

  if (type === 'detail') {
    return (
      <div className="fund-fade-in">
        <Skeleton.Input active block style={{ height: 160, marginBottom: 16, borderRadius: 12 }} />
        <Skeleton.Input active block style={{ height: 350, marginBottom: 16, borderRadius: 12 }} />
        <Row gutter={16}>
          <Col span={12}>
            <Skeleton active paragraph={{ rows: 5 }} />
          </Col>
          <Col span={12}>
            <Skeleton.Input active block style={{ height: 280, borderRadius: 12 }} />
          </Col>
        </Row>
      </div>
    );
  }

  if (type === 'form') {
    return (
      <div className="fund-fade-in" style={{ maxWidth: 600, margin: '0 auto' }}>
        <Skeleton.Input active block style={{ height: 40, marginBottom: 24, borderRadius: 8 }} />
        {[1, 2, 3, 4].map((i) => (
          <div key={i} style={{ marginBottom: 24 }}>
            <Skeleton.Input active style={{ width: 80, height: 16, marginBottom: 8 }} />
            <Skeleton.Input active block style={{ height: 36, borderRadius: 8 }} />
          </div>
        ))}
      </div>
    );
  }

  // list
  return (
    <div className="fund-fade-in">
      {[1, 2, 3, 4, 5].map((i) => (
        <Skeleton.Input key={i} active block style={{ height: 56, marginBottom: 8, borderRadius: 8 }} />
      ))}
    </div>
  );
};

export default PageSkeleton;
