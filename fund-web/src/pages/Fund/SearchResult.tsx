import React, { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { List, Tag, Button, Empty } from 'antd';
import { StarOutlined, StarFilled, PlusOutlined } from '@ant-design/icons';
import { fundApi } from '../../api/fund';
import { watchlistApi } from '../../api/watchlist';
import { formatFundType, FUND_TYPE_TAG_COLOR } from '../../utils/format';
import { message } from 'antd';
import { useSearchStore } from '../../store/searchStore';
import PageSkeleton from '../../components/PageSkeleton';

const SearchResult: React.FC = () => {
  const [searchParams] = useSearchParams();
  const urlKeyword = searchParams.get('q') || '';
  const navigate = useNavigate();
  const { keyword: storeKeyword, results, setSearch } = useSearchStore();
  const [loading, setLoading] = useState(false);
  const [watchlistSet, setWatchlistSet] = useState<Set<string>>(new Set());

  const keyword = urlKeyword || storeKeyword;

  useEffect(() => {
    if (!urlKeyword) return;
    if (storeKeyword === urlKeyword && results.length > 0) return;

    setLoading(true);
    fundApi.search(urlKeyword)
      .then((res) => {
        const list = res.list || [];
        setSearch(urlKeyword, list);
        if (list.length > 0) {
          const codes = list.map((f) => f.code);
          watchlistApi.checkExists(codes)
            .then((existCodes) => setWatchlistSet(new Set(existCodes)))
            .catch(() => {});
        }
      })
      .catch(() => setSearch(urlKeyword, []))
      .finally(() => setLoading(false));
  }, [urlKeyword]);

  useEffect(() => {
    if (results.length > 0 && watchlistSet.size === 0) {
      const codes = results.map((f) => f.code);
      watchlistApi.checkExists(codes)
        .then((existCodes) => setWatchlistSet(new Set(existCodes)))
        .catch(() => {});
    }
  }, [results]);

  const handleAddWatchlist = async (code: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (watchlistSet.has(code)) return;
    try {
      await watchlistApi.add({ fundCode: code });
      message.success('已添加到自选');
      setWatchlistSet((prev) => new Set([...prev, code]));
    } catch { /* error handled by interceptor */ }
  };

  if (loading) return <PageSkeleton type="list" />;

  if (!keyword) return <Empty description="请输入搜索关键词" style={{ padding: '80px 0' }} />;

  return (
    <div className="fund-fade-in">
      <div className="fund-page-header">
        <h2>搜索结果</h2>
        <div className="fund-subtitle">关键词 "{keyword}" · 共 {results.length} 条结果</div>
      </div>
      <List
        dataSource={results}
        locale={{ emptyText: '未找到相关基金' }}
        split={false}
        renderItem={(item) => {
          const inWatchlist = watchlistSet.has(item.code);
          return (
            <div
              className="fund-list-item"
              style={{ marginBottom: 4 }}
              onClick={() => navigate(`/fund/${item.code}`)}
            >
              <div style={{ display: 'flex', alignItems: 'center', flex: 1 }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <span className="fund-code">{item.code}</span>
                    <span className="fund-name">{item.name}</span>
                  </div>
                  <div style={{ marginTop: 4 }}>
                    <Tag color={FUND_TYPE_TAG_COLOR[item.type] || 'default'} style={{ fontSize: 11 }}>
                      {formatFundType(item.type)}
                    </Tag>
                  </div>
                </div>
              </div>
              <div style={{ display: 'flex', gap: 8 }} onClick={(e) => e.stopPropagation()}>
                <Button
                  size="small"
                  type={inWatchlist ? 'text' : 'default'}
                  icon={inWatchlist ? <StarFilled style={{ color: '#faad14' }} /> : <StarOutlined />}
                  style={inWatchlist ? { color: '#faad14' } : undefined}
                  onClick={(e) => handleAddWatchlist(item.code, e)}
                >
                  {inWatchlist ? '已自选' : '自选'}
                </Button>
                <Button
                  size="small"
                  type="primary"
                  ghost
                  icon={<PlusOutlined />}
                  onClick={(e) => {
                    e.stopPropagation();
                    navigate(`/portfolio/add?fundCode=${item.code}`);
                  }}
                >
                  持仓
                </Button>
              </div>
            </div>
          );
        }}
      />
    </div>
  );
};

export default SearchResult;
