import React, { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { List, Tag, Button, Spin, Empty } from 'antd';
import { StarOutlined, PlusOutlined } from '@ant-design/icons';
import { fundApi, type FundSearchItem } from '../../api/fund';
import { watchlistApi } from '../../api/watchlist';
import { formatFundType } from '../../utils/format';
import { message } from 'antd';
import { useSearchStore } from '../../store/searchStore';

const SearchResult: React.FC = () => {
  const [searchParams] = useSearchParams();
  const urlKeyword = searchParams.get('q') || '';
  const [results, setResults] = useState<FundSearchItem[]>([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const searchStore = useSearchStore();

  // 优先使用 URL 参数，否则回退到 Zustand 缓存
  const keyword = urlKeyword || searchStore.keyword;

  useEffect(() => {
    if (!keyword) return;

    // 如果是 URL 参数触发 或 store 已有缓存
    if (searchStore.keyword === keyword && searchStore.results.length > 0) {
      setResults(searchStore.results);
      // 非 URL 参数（tab 切换回来）不需要重新请求
      if (!urlKeyword) return;
      // URL 参数和 store 关键词一致，也不需要重新请求
      return;
    }

    // 没有 URL 参数且 store 无缓存，无法搜索
    if (!urlKeyword) return;

    setLoading(true);
    fundApi.search(keyword)
      .then((res) => {
        const list = res.list || [];
        setResults(list);
        searchStore.setSearch(keyword, list);
      })
      .catch(() => setResults([]))
      .finally(() => setLoading(false));
  }, [keyword, urlKeyword]);

  const handleAddWatchlist = async (code: string, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await watchlistApi.add({ fundCode: code });
      message.success('已添加到自选');
    } catch { /* error handled by interceptor */ }
  };

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;

  if (!keyword) return <Empty description="请输入搜索关键词" />;

  return (
    <div>
      <h3>搜索结果: "{keyword}" ({results.length} 条)</h3>
      <List
        dataSource={results}
        locale={{ emptyText: '未找到相关基金' }}
        renderItem={(item) => (
          <List.Item
            style={{ cursor: 'pointer', background: '#fff', marginBottom: 8, padding: '12px 16px', borderRadius: 8 }}
            onClick={() => navigate(`/fund/${item.code}`)}
            actions={[
              <Button
                key="star"
                size="small"
                icon={<StarOutlined />}
                onClick={(e) => handleAddWatchlist(item.code, e)}
              >
                自选
              </Button>,
              <Button
                key="add"
                size="small"
                type="primary"
                icon={<PlusOutlined />}
                onClick={(e) => {
                  e.stopPropagation();
                  navigate(`/portfolio/add?fundCode=${item.code}`);
                }}
              >
                持仓
              </Button>,
            ]}
          >
            <List.Item.Meta
              title={
                <span>
                  <span style={{ fontWeight: 600 }}>{item.code}</span>
                  <span style={{ marginLeft: 12 }}>{item.name}</span>
                </span>
              }
              description={<Tag>{formatFundType(item.type)}</Tag>}
            />
          </List.Item>
        )}
      />
    </div>
  );
};

export default SearchResult;
